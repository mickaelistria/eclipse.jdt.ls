package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;

class LinkResourceUtil {

	private static final IPath METADATA_FOLDER_PATH = ResourcesPlugin.getWorkspace().getRoot().getLocation().append(".projects");

	private static boolean isNewer(Path file, Instant instant) throws CoreException {
		try {
			BasicFileAttributes attributes = Files.getFileAttributeView(file, BasicFileAttributeView.class).readAttributes();
			return attributes.creationTime().toInstant().isAfter(instant);
		} catch (IOException ex) {
			throw new CoreException(Status.error(ex.getMessage(), ex));
		}
	}

    /**
     * Get the redirected path of the input path. The path will be redirected to
     * the workspace's metadata folder ({@link JLSFsUtils#METADATA_FOLDER_PATH}).
     * @param projectName name of the project.
     * @param path path needs to be redirected.
     * @return the redirected path.
     */
    private static IPath getMetaDataFilePath(String projectName, IPath path) {
        if (path.segmentCount() == 1) {
            return METADATA_FOLDER_PATH.append(projectName).append(path);
        }

        String lastSegment = path.lastSegment();
        if (IProjectDescription.DESCRIPTION_FILE_NAME.equals(lastSegment)) {
            return METADATA_FOLDER_PATH.append(projectName).append(lastSegment);
        }

        return null;
    }

	private static void linkFolderIfNewer(IFolder settingsFolder, Instant instant) throws CoreException {
		if (settingsFolder.isLinked()) {
			return;
		}
		if (settingsFolder.exists() && !isNewer(settingsFolder.getLocation().toPath(), instant)) {
			return;
		}
		if (!settingsFolder.exists()) {
			// not existing yet, create link
			File diskFolder = getMetaDataFilePath(settingsFolder.getProject().getName(), settingsFolder.getProjectRelativePath()).toFile();
			diskFolder.mkdirs();
			settingsFolder.createLink(diskFolder.toURI(), IResource.NONE, new NullProgressMonitor());
		} else if (isNewer(settingsFolder.getLocation().toPath(), instant)) {
			// already existing but not existing before import: move then link
			File sourceFolder = settingsFolder.getLocation().toFile();
			File targetFolder = getMetaDataFilePath(settingsFolder.getProject().getName(), settingsFolder.getProjectRelativePath()).toFile();
			sourceFolder.renameTo(targetFolder);
			settingsFolder.createLink(targetFolder.toURI(), IResource.REPLACE, new NullProgressMonitor());
		}
	}

	private static void linkFileIfNewer(IFile metadataFile, Instant instant) throws CoreException {
		if (metadataFile.isLinked()) {
			return;
		}
		if (metadataFile.exists() && !isNewer(metadataFile.getLocation().toPath(), instant)) {
			return;
		}
		File targetFile = getMetaDataFilePath(metadataFile.getProject().getName(), metadataFile.getProjectRelativePath()).toFile();
		if (!targetFile.exists()) {
			try {
				targetFile.getParentFile().mkdirs();
				targetFile.createNewFile();
			} catch (IOException ex) {
				throw new CoreException(Status.error(targetFile + " cannot be created", ex)); //$NON-NLS-1$
			}
		}
		if (metadataFile.exists()) {
			metadataFile.getLocation().toFile().renameTo(targetFile);
		} else {
			try {
				Files.writeString(targetFile.toPath(), "<factorypath/>");
			} catch (IOException ex) {
				throw new CoreException(Status.error(ex.getMessage(), ex));
			}
		}
		metadataFile.createLink(targetFile.toURI(), IResource.REPLACE, new NullProgressMonitor());
	}

	public static void linkResourcesIfNewer(IProject project, Instant instant) throws CoreException {
		linkFileIfNewer(project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME), instant);
		linkFolderIfNewer(project.getFolder(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME), instant);
		linkFileIfNewer(project.getFile(IJavaProject.CLASSPATH_FILE_NAME), instant);
		linkFileIfNewer(project.getFile(".factorypath"), instant);
	}

	/*public static void linkDotProject(IProject project) throws CoreException {
		IFile dotProject = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		if (dotProject.isLinked()) {
			return;
		}
		File targetDiskFile = getMetaDataFilePath(project.getName(), dotProject.getProjectRelativePath()).toFile();
		if (!targetDiskFile.exists()) {
			try {
				targetDiskFile.getParentFile().mkdirs();
				Files.copy(dotProject.getLocation().toPath(), targetDiskFile.toPath());
			} catch (Exception ex) {
				throw new CoreException(Status.error(targetDiskFile + " cannot be created", ex)); //$NON-NLS-1$
			}
		}
		File sourceDiskFile = dotProject.getLocation().toFile();
		dotProject.createLink(IPath.fromFile(targetDiskFile), IResource.FORCE | IResource.REPLACE, null);
		sourceDiskFile.delete();
	}*/

}
