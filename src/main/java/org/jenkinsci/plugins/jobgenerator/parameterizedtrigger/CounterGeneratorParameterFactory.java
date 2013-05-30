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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameterFactory;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameterFactoryDescriptor;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameters;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CounterGeneratorParameterFactory extends
        AbstractBuildParameterFactory {

	private final String from;
	private final String to;
	private final String step;
	private final String paramExpr;
	private final SteppingValidationEnum validationFail;

	public enum SteppingValidationEnum {
		FAIL("Fail the build step"), // previous behaviour (default)
		SKIP("Don't trigger these projects") {
			@Override
			public void failCheck(TaskListener listener)
			        throws AbstractBuildParameters.DontTriggerException {
				listener.getLogger()
				        .println(
				                Messages.CounterGeneratorParameterFactory_CountingWillNotTerminateSkipping());
				throw new AbstractBuildParameters.DontTriggerException();
			}
		},
		NOPARMS("Skip these parameters") {
			@Override
			public void failCheck(TaskListener listener)
			        throws AbstractBuildParameters.DontTriggerException {
				listener.getLogger()
				        .println(
				                Messages.CounterGeneratorParameterFactory_CountingWillNotTerminateIgnore());
			}
		};

		private String description;

		public String getDescription() {
			return description;
		}

		SteppingValidationEnum(String description) {
			this.description = description;
		}

		public void failCheck(TaskListener listener)
		        throws AbstractBuildParameters.DontTriggerException {
			throw new RuntimeException(
			        Messages.CounterGeneratorParameterFactory_CountingWillNotTerminate());
		}
	}

	public CounterGeneratorParameterFactory(long from, long to, long step,
	        String paramExpr) {
		this(Long.toString(from), Long.toString(to), Long.toString(step),
		        paramExpr);
	}

	public CounterGeneratorParameterFactory(long from, long to, long step,
	        String paramExpr, SteppingValidationEnum validationFail) {
		this(Long.toString(from), Long.toString(to), Long.toString(step),
		        paramExpr, validationFail);
	}

	public CounterGeneratorParameterFactory(String from, String to,
	        String step, String paramExpr) {
		// mimic old behaviour which failed job
		this(from, to, step, paramExpr, SteppingValidationEnum.FAIL);
	}

	@DataBoundConstructor
	public CounterGeneratorParameterFactory(String from, String to,
	        String step, String paramExpr, SteppingValidationEnum validationFail) {
		this.from = from;
		this.to = to;
		this.step = step;
		this.paramExpr = paramExpr;
		this.validationFail = validationFail;
	}

	@Override
	public List<AbstractBuildParameters> getParameters(
	        AbstractBuild<?, ?> build, TaskListener listener)
	        throws IOException, InterruptedException,
	        AbstractBuildParameters.DontTriggerException {
		EnvVars envVars = build.getEnvironment(listener);

		long fromNum = Long.valueOf(envVars.expand(from));
		long toNum = Long.valueOf(envVars.expand(to));
		long stepNum = Long.valueOf(envVars.expand(step));

		ArrayList<AbstractBuildParameters> params = Lists.newArrayList();
		int upDown = Long.signum(toNum - fromNum);

		if (upDown == 0) {
			params.add(getParameterForCount(fromNum));
		} else {
			if (stepNum == 0) {
				validationFail.failCheck(listener);
			} else if (upDown * stepNum < 0) {
				validationFail.failCheck(listener);
			} else {
				for (Long i = fromNum; upDown * i <= upDown * toNum; i += stepNum) {
					params.add(getParameterForCount(i));
				}
			}
		}
		return params;
	}

	private PredefinedGeneratorParameters getParameterForCount(Long i) {
		String stringWithCount = Util.replaceMacro(paramExpr,
		        ImmutableMap.of("COUNT", i.toString()));
		return new PredefinedGeneratorParameters(stringWithCount);
	}

	@Extension
	public static class DescriptorImpl extends
	        AbstractBuildParameterFactoryDescriptor {
		@Override
		public String getDisplayName() {
			return Messages
			        .CounterGeneratorParameterFactory_CounterGeneratorParameterFactory();
		}

		public FormValidation doCheckFrom(@QueryParameter String value) {
			return validateNumberField(value);
		}

		public FormValidation doCheckTo(@QueryParameter String value) {
			return validateNumberField(value);
		}

		public FormValidation doCheckStep(@QueryParameter String value) {
			return validateNumberField(value);
		}

		private FormValidation validateNumberField(String value) {
			// The field can contain Parameters - eliminate them first. The
			// remaining String should
			// be empty or a number.
			String valueWithoutVariables = Util.replaceMacro(value,
			        EMPTY_STRING_VARIABLE_RESOLVER);
			if (StringUtils.isNotEmpty(valueWithoutVariables)
			        && !isNumber(valueWithoutVariables)) {
				return FormValidation.warning(hudson.model.Messages
				        .Hudson_NotANumber());
			} else {
				return FormValidation.validateRequired(value);
			}
		}

		private boolean isNumber(String value) {
			try {
				Long.valueOf(value);
			} catch (NumberFormatException e) {
				return false;
			}
			return true;
		}
	}

	public String getFrom() {
		return from;
	}

	public String getTo() {
		return to;
	}

	public String getStep() {
		return step;
	}

	public String getParamExpr() {
		return paramExpr;
	}

	public SteppingValidationEnum getvalidationFail() {
		return validationFail;
	}

	private static final VariableResolver<String> EMPTY_STRING_VARIABLE_RESOLVER = new VariableResolver<String>() {

		@Override
		public String resolve(String name) {
			return "";
		}
	};

}
