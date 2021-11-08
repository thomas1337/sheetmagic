package com.coffeestorm.dsl.sheetmagic.ui.actions;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.generator.GeneratorContext;
import org.eclipse.xtext.generator.IFileSystemAccess2;

import com.coffeestorm.dsl.sheetmagic.generator.SheetmagicGenerator;
import com.coffeestorm.dsl.sheetmagic.ui.internal.SheetmagicActivator;

public class GenerateJavascriptAction extends AbstractTransformAction {
	
	public GenerateJavascriptAction() {
		this.fileExtensions 		= new String[]{"sm"};
		this.canceledMessage 		= "Generation was canceled";
		this.noFileSelectedMessage 	= "Please select a SM model file";
	}
	
	@Override
	protected void init() throws CoreException, ClassNotFoundException {
		this.outputFolderName = "src-gen";
		super.init();
		this.jobTitle = "Generating Code Artifacts"; 
	}
	
	@Override
	protected void acquireInjector() {
		this.injector =  SheetmagicActivator.getInstance().getInjector(SheetmagicActivator.COM_COFFEESTORM_DSL_SHEETMAGIC_SHEETMAGIC);
	}

	@Override
	protected void doTransform(IProgressMonitor monitor, Resource resource) {
		monitor.worked(1);
		SheetmagicGenerator generator = new SheetmagicGenerator(); 
		this.injector.injectMembers(generator);
		IFileSystemAccess2 fsa = getConfiguredFileSystemAccess2("DEFAULT_OUTPUT", getOutputFolder());
		System.out.println("Javascript Generator Starting");
		generator.doGenerate(resource, fsa, new GeneratorContext()); 
		System.out.println("Javascript Generator Terminating");
	}
	
}
