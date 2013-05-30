/*
The MIT License

Copyright (c) 2013, Sylvain Benner.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package org.jenkinsci.plugins.jobgenerator.parameterizedtrigger;

import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameterFactory;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameterFactoryDescriptor;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameters;
import hudson.remoting.VirtualChannel;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;
import hudson.util.VariableResolver;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.DataBoundConstructor;

public class FileGeneratorParameterFactory extends
        AbstractBuildParameterFactory {

	/**
	 * Enum containing the action that could occur when there are no files found
	 * in the workspace
	 * 
	 */
	public enum NoFilesFoundEnum {
		SKIP("Don't trigger these projects") { // previous behaviour (default)
			@Override
			public void failCheck(TaskListener listener)
			        throws AbstractBuildParameters.DontTriggerException {
				listener.getLogger()
				        .println(
				                Messages.FileGeneratorParameterFactory_NoFilesFoundSkipping());
				throw new AbstractBuildParameters.DontTriggerException();
			}
		},
		NOPARMS("Skip these parameters") {
			@Override
			public void failCheck(TaskListener listener)
			        throws AbstractBuildParameters.DontTriggerException {
				listener.getLogger()
				        .println(
				                Messages.FileGeneratorParameterFactory_NoFilesFoundIgnore());
			}
		},
		FAIL("Fail the build step") {
			@Override
			public void failCheck(TaskListener listener)
			        throws AbstractBuildParameters.DontTriggerException {
				listener.getLogger()
				        .println(
				                Messages.FileGeneratorParameterFactory_NoFilesFoundTerminate());
				throw new RuntimeException();
			}
		};

		private String description;

		public String getDescription() {
			return description;
		}

		NoFilesFoundEnum(String description) {
			this.description = description;
		}

		public abstract void failCheck(TaskListener listener)
		        throws AbstractBuildParameters.DontTriggerException;
	}

	private final String filePattern;
	private final NoFilesFoundEnum noFilesFoundAction;

	@DataBoundConstructor
	public FileGeneratorParameterFactory(String filePattern,
	        NoFilesFoundEnum noFilesFoundAction) {
		this.filePattern = filePattern;
		this.noFilesFoundAction = noFilesFoundAction;
	}

	public FileGeneratorParameterFactory(String filePattern) {
		this(filePattern, NoFilesFoundEnum.SKIP);
	}

	public String getFilePattern() {
		return filePattern;
	}

	public NoFilesFoundEnum getNoFilesFoundAction() {
		return noFilesFoundAction;
	}

	@Override
	public List<AbstractBuildParameters> getParameters(
	        AbstractBuild<?, ?> build, TaskListener listener)
	        throws IOException, InterruptedException,
	        AbstractBuildParameters.DontTriggerException {

		List<AbstractBuildParameters> result = Lists.newArrayList();

		try {
			FilePath workspace = getWorkspace(build);
			FilePath[] files = workspace.list(getFilePattern());
			if (files.length == 0) {
				noFilesFoundAction.failCheck(listener);
			} else {
				for (FilePath f : files) {
					String parametersStr = f.readToString();
					Logger.getLogger(
					        FileGeneratorParameterFactory.class.getName()).log(
					        Level.INFO, null,
					        "Triggering build with " + f.getName());
					result.add(new PredefinedGeneratorParameters(parametersStr));
				}
			}
		} catch (IOException ex) {
			Logger.getLogger(FileGeneratorParameterFactory.class.getName())
			        .log(Level.SEVERE, null, ex);
		}

		return result;
	}

	private FilePath getWorkspace(AbstractBuild build) {
		FilePath workspace = build.getWorkspace();
		if (workspace == null) {
			workspace = build.getProject().getSomeWorkspace();
		}
		return workspace;
	}

	@Extension
	public static class DescriptorImpl extends
	        AbstractBuildParameterFactoryDescriptor {
		@Override
		public String getDisplayName() {
			return Messages.FileGeneratorParameterFactory_FileGeneratorParameterFactory();
		}
	}
}
