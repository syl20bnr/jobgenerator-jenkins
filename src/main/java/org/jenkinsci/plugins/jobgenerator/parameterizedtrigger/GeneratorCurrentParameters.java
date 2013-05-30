/*
The MIT License

Copyright (c) 2012-2013, Sylvain Benner.

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

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.TaskListener;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import org.jenkinsci.plugins.jobgenerator.parameters.*;

/**
 * Generator Build Parameter of the current build.
 * This class is an add-on to the parameterized build trigger plugin.
 * 
 * @author <a href="mailto:sylvain.benner@gmail.com">Sylvain Benner</a>
 */
public class GeneratorCurrentParameters extends AbstractBuildParameters {

    @DataBoundConstructor
    public GeneratorCurrentParameters() {
    }

    @Override
    public Action getAction(AbstractBuild<?, ?> build, TaskListener listener)
            throws IOException {

        ParametersAction action = build.getAction(ParametersAction.class);
        if (action == null) {
            listener.getLogger().println(
                    "[parameterized-trigger] Current build has no " + 
                    "build parameters.");
            return null;
        } else {
            List<ParameterValue> values = new ArrayList<ParameterValue>(action
                    .getParameters().size());
            for (ParameterValue value : action.getParameters()){
                if(GeneratorKeyValueParameterValue.class.isInstance(value)){
                    values.add(value);
                }
            }
            return values.isEmpty() ? null : new ParametersAction(values);
        }
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends
            Descriptor<AbstractBuildParameters> {

        @Override
        public String getDisplayName() {
            return "Current generator parameters";
        }

    }

}
