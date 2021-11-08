package com.coffeestorm.dsl.sheetmagic.ui.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.coffeestorm.dsl.sheetmagic.ui.internal.SheetmagicActivator;

public class PathUtil {

	public static IResource getPlatformResource(Resource eResource) {
		URI eUri = eResource.getURI();
		if (eUri.isPlatformResource()) {
			String platformString = eUri.toPlatformString(true);
			return ResourcesPlugin.getWorkspace().getRoot().findMember(platformString);
		}
		return null;
	}

	/** answer the project relative path of the given file. */
	public static String getRelativeFileName(IFile file) {
		return file.getProjectRelativePath().toPortableString();
	}

	/** answer the source folder containing the given relative name, an empty string if not matched. */
	public static String getSourceFolder(IJavaProject javaProject, String projectRelativeName) throws JavaModelException {
		if (javaProject != null) {
			for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
				IResource resource = root.getResource();
				if (resource == null)
					continue;
				String sourceFolder = resource.getProjectRelativePath().toPortableString()+"/";
				if (projectRelativeName.startsWith(sourceFolder))
					return sourceFolder;
			}		
		}
		return "";
	}

	public static IJavaProject getJavaProject(IResource iResource) throws CoreException {
		IJavaProject javaProject = JavaCore.create(iResource.getProject());
		if (!javaProject.exists())
			throw new CoreException(new Status(IStatus.ERROR, SheetmagicActivator.PLUGIN_ID, "Containing Java Project not found for "+iResource.getName()));
		return javaProject;
	}

	public static String getProjectName(Resource eResource) {
		return getPlatformResource(eResource).getProject().getName();
	}
	
	public static IClasspathEntry[] getProjectClasspath (Resource eResource) {
		try {
			IJavaProject javaProject = getJavaProject(getPlatformResource(eResource));
			return javaProject.getResolvedClasspath(true);
		} catch (CoreException e) {
			SheetmagicActivator.getInstance().getLog().log(new Status(IStatus.ERROR, SheetmagicActivator.PLUGIN_ID, "Problem retrieving project classpath", e));
		}
		return null;
	}

	public static String[][] getSourceFolders(IJavaProject javaProject) throws JavaModelException {
		List<String[]> resultList = new ArrayList<String[]>();
		for (IClasspathEntry cpEntry : javaProject.getResolvedClasspath(true))
			if (cpEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE)
				resultList.add(cpEntry.getPath().segments());
		return resultList.toArray(new String[resultList.size()][]);
	}

}
