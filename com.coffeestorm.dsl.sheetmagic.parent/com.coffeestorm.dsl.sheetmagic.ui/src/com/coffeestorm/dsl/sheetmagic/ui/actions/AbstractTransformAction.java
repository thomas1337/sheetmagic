package com.coffeestorm.dsl.sheetmagic.ui.actions;

import static org.eclipse.xtext.resource.XtextResource.OPTION_ENCODING;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.Resource.Diagnostic;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.mwe.core.issues.Issues;
import org.eclipse.emf.mwe.core.issues.IssuesImpl;
import org.eclipse.emf.mwe.core.issues.MWEDiagnostic;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.generator.AbstractFileSystemAccess2;
import org.eclipse.xtext.generator.IFileSystemAccess2;
import org.eclipse.xtext.generator.JavaIoFileSystemAccess;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;
import org.eclipse.xtext.ui.editor.utils.EditorUtils;
import org.eclipse.xtext.ui.editor.validation.IValidationIssueProcessor;
import org.eclipse.xtext.ui.resource.XtextResourceSetProvider;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.Issue;

import com.coffeestorm.dsl.sheetmagic.ui.internal.SheetmagicActivator;
import com.google.inject.ConfigurationException;
import com.google.inject.Injector;

public abstract class AbstractTransformAction extends AbstractHandler implements IObjectActionDelegate {

	protected String[] fileExtensions;
	protected String canceledMessage;
	protected String jobTitle;
	protected String noFileSelectedMessage;
	protected String outputFolderName;
	protected List<String> folderNamesToRefresh; 
	
	protected Shell shell;
	private List<IFile> selectedFiles;
	protected IProject project;
	private IJavaProject javaProject;
	protected Injector injector;

	public AbstractTransformAction() {
		super();
	}

	/**
	 * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
	 */
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		shell = targetPart.getSite().getShell();
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
	    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	    if (window != null) {
	    	ISelection selection  = window.getSelectionService().getSelection();
	    	if (selection instanceof ITextSelection){
				XtextEditor xtextEditor = EditorUtils.getActiveXtextEditor(event);
				if (xtextEditor != null) {
					xtextEditor.getDocument();
					IResource resource = xtextEditor.getResource();
					if (resource instanceof IFile){
						this.selectedFiles = new ArrayList<IFile>();
						this.selectedFiles.add((IFile)resource);
					}
				}
	    	}else{
	    		setSelectedFile(selection);
	    	}
	      setActivePart(null,window.getActivePage().getActivePart());
	    }
	    this.run(null);
	    return null;
	}
	/**
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		if (this.selectedFiles == null || this.selectedFiles.isEmpty()) {
			MessageDialog.openInformation(shell, 
										  "Transform", 
										  this.noFileSelectedMessage);
			return;
		}
		try {
			init();
			initOutputFolder();
		} catch (ClassNotFoundException e) {
			MessageDialog.openInformation(shell, "Transform", "Unable to load generator: "+e.getMessage());
			return;
		} catch (CoreException ex) {
			MessageDialog.openInformation(shell, "Transform", "Input problem: "+ex.getMessage());
			return;
		} catch (OperationCanceledException ex) {
			return; // no problem
		}
		Job job = new Job(this.jobTitle) {
		    protected IStatus run(IProgressMonitor monitor) {
			    	Issues issues;
						try {
							issues = internalRun(monitor);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							return Status.OK_STATUS;
						}
		        if (issues.hasErrors()) {
		        	StringBuffer buf = new StringBuffer();
		        	for (MWEDiagnostic err : issues.getErrors()) {
						buf.append(err.getMessage());
						buf.append('\n');
					}
					return new Status(IStatus.ERROR, SheetmagicActivator.PLUGIN_ID, buf.toString());
		        } else if (issues.hasWarnings()) {
		        	logIssues(issues);
				}
		        return Status.OK_STATUS;
		    }
		};
		setSchedulingRules(job);
		job.setUser(true);
		job.schedule();
	}

	public static void logIssues(Issues issues) {
		ILog log = SheetmagicActivator.getInstance().getLog();
		for (MWEDiagnostic warn : issues.getWarnings()) {
			log.log(MWEDiagnostic.toIStatus(warn));
		}
	}

	/** ensure we can access both the input file and the output directory */
	private void setSchedulingRules(Job job) {
		IResourceRuleFactory ruleFactory = ResourcesPlugin.getWorkspace().getRuleFactory();
        ISchedulingRule inFileRule = null;
        for (IFile file : this.selectedFiles) {
        	inFileRule = MultiRule.combine(inFileRule, ruleFactory.createRule(file));
		}
        ISchedulingRule outDirRule = null;
        for (String folderName : this.folderNamesToRefresh) {
        	outDirRule = MultiRule.combine(outDirRule, ruleFactory.createRule(this.project.findMember(folderName)));
		}
        job.setRule(MultiRule.combine(inFileRule, outDirRule));
	}

	protected Issues internalRun(IProgressMonitor monitor) throws Exception {
		// fetch early so refresh will not be disturbed by parallel executions which may modify fields meanwhile: 
		IProject myProject = this.project;
		List<String> myFoldersToRefresh = this.folderNamesToRefresh;

		IssuesImpl issues = new IssuesImpl();
		monitor.beginTask(this.jobTitle, 3+11*this.selectedFiles.size());
		try {
			acquireInjector();
			monitor.worked(2);
			XtextResourceSetProvider rsSetProvider = (XtextResourceSetProvider) injector.getInstance(XtextResourceSetProvider.class);
			monitor.worked(1);
			IResourceServiceProvider.Registry registry = IResourceServiceProvider.Registry.INSTANCE;
			for (IFile file : this.selectedFiles) {
				if (monitor.isCanceled()) return issues;
				Resource resource = loadResource(file, new SubProgressMonitor(monitor, 2, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK), rsSetProvider, registry);
				List<Diagnostic> errors = resource.getErrors();
				if (errors != null && errors.size() > 0) {
					for (Diagnostic error : errors)
						issues.addError(error.getMessage());
					return issues;
				}
				doTransform(monitor, resource);
			}
			postProcess(myProject, myFoldersToRefresh, monitor, issues);
		} catch (CoreException e) {
			issues.addError(e.getMessage());
		} catch (IOException e) {
			issues.addError(e.getMessage());
		}
		monitor.done();
		return issues;
	}

	protected abstract void acquireInjector();

	protected abstract void doTransform(IProgressMonitor monitor, Resource resource);
	
	private Resource loadResource(IFile file, final IProgressMonitor monitor, XtextResourceSetProvider rsSetProvider, IResourceServiceProvider.Registry registry) 
			throws Exception 
	{
		monitor.beginTask("Reading resource ...", 3);
		monitor.subTask(file.getName());
		ResourceSet rs = rsSetProvider.get(project);
		String encoding = file.getCharset();
		rs.getLoadOptions().put(OPTION_ENCODING, encoding);
		Resource resource = rs.createResource(URI.createPlatformResourceURI(file.getFullPath().toString()));
		monitor.worked(1);
		Map<Object,Object> loadOptions = new HashMap<Object,Object>();
		loadOptions.put(OPTION_ENCODING, encoding);
		resource.load(loadOptions);
		monitor.worked(1);
		IResourceServiceProvider provider = registry.getResourceServiceProvider(resource.getURI());
		List<Issue> issues = provider.getResourceValidator().validate(resource, CheckMode.ALL, null);
		List<Issue> errors = new ArrayList<Issue>();
		for (Issue i : issues) {
			if (i.getSeverity().equals(Severity.ERROR)) {
				errors.add(i);
			}
		}
		if (errors.size() > 0) {
			throw new Exception("There are validation errors in your Model. Please resolve them first.");
		}
		monitor.done();
		return resource;
	}

	
	protected IFileSystemAccess2 getConfiguredFileSystemAccess2(String outletName, String outletPath) {
		AbstractFileSystemAccess2 configuredFileSystemAccess;
		try {
			configuredFileSystemAccess = this.injector.getInstance(AbstractFileSystemAccess2.class);
		} catch (ConfigurationException ce) {
			configuredFileSystemAccess = this.injector.getInstance(JavaIoFileSystemAccess.class);
		}
		configuredFileSystemAccess.setOutputPath(outletName, outletPath);
		return configuredFileSystemAccess;
	}
	
	void ensureOutputFolder(String folderName) throws CoreException {
		IFolder folder = this.project.getFolder(folderName);
		if (!folder.exists())
			createRecursive(folder);
	}
	protected void createRecursive(final IContainer resource) throws CoreException {
		if (!resource.exists()) {
			if (!resource.getParent().exists()) {
				createRecursive(resource.getParent());
			}
			if (resource instanceof IFolder) {
				((IFolder) resource).create(false, true, null);
			}
		}
	}
	
	protected String getOutputFolder() {
		return this.project.getLocation().toOSString()+File.separatorChar+this.outputFolderName;
	}

	protected void postProcess(IProject myProject, List<String> foldersToRefresh, IProgressMonitor monitor, Issues issues) throws CoreException {
		monitor.subTask("Cleaning up");
		for (String folderName : foldersToRefresh)
			myProject.findMember(folderName).refreshLocal(IResource.DEPTH_INFINITE, monitor);
	}

	public void setSelectedFile(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			this.selectedFiles = new ArrayList<IFile>();
			@SuppressWarnings("rawtypes")
			Iterator iterator = ((IStructuredSelection) selection).iterator();
			while (iterator.hasNext()) {
				Object obj = iterator.next();
				if (obj instanceof IFile) {
					IFile file = (IFile) obj;
					for (String expectedExtension : this.fileExtensions) {
						if (expectedExtension.equals(file.getFileExtension())) {
							this.selectedFiles.add(file);
							break;
						}
					}
				} else if (obj instanceof IFolder) {
					addFilesFromFolder(this.selectedFiles, (IFolder)obj);
				}
			}
			if (this.selectedFiles.size() > 0) {
				return;
			}
		}
		this.selectedFiles = null;
	}
	/**
	 * Watch selection changes so we know the selected file when the action is invoked.
	 * @see IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			this.selectedFiles = new ArrayList<IFile>();
			@SuppressWarnings("rawtypes")
			Iterator iterator = ((IStructuredSelection) selection).iterator();
			while (iterator.hasNext()) {
				Object obj = iterator.next();
				if (obj instanceof IFile) {
					IFile file = (IFile) obj;
					for (String expectedExtension : this.fileExtensions) {
						if (expectedExtension.equals(file.getFileExtension())) {
							this.selectedFiles.add(file);
							break;
						}
					}
				} else if (obj instanceof IFolder) {
					addFilesFromFolder(this.selectedFiles, (IFolder)obj);
				}
			}
			if (this.selectedFiles.size() > 0) {
				action.setEnabled(true);
				return;
			}
		}
		action.setEnabled(false);
		this.selectedFiles = null;
	}
	protected void addFilesFromFolder(List<IFile> selectedFiles, IFolder folder) {
	}

	protected void init() throws CoreException, ClassNotFoundException {
		this.folderNamesToRefresh = new ArrayList<String>();
		this.project = null;
		for (IFile file : this.selectedFiles) {
			IProject currentProject = file.getProject();
			if (this.project != null) {
				if (!this.project.equals(currentProject))
					throw new CoreException(new Status(IStatus.ERROR, SheetmagicActivator.PLUGIN_ID, "Cannot process files from multiple projects in the same generator invocation."));
			} else {
				this.project = currentProject;
				if (this.project.hasNature(JavaCore.NATURE_ID))
					this.javaProject = JavaCore.create(this.project);
			}
			registerFile(file);
		}
	}

	private void initOutputFolder() throws CoreException {
		ensureOutputFolder(this.outputFolderName);
		this.folderNamesToRefresh.add(this.outputFolderName);
	}

	protected void registerFile(IFile file)	throws CoreException {
		String projectRelativeName = PathUtil.getRelativeFileName(file);
		String sourceFolderName = PathUtil.getSourceFolder(this.javaProject, projectRelativeName);
		this.folderNamesToRefresh.add(sourceFolderName);
	}
}
