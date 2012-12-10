/*
The MIT License

Copyright (c) 2012, Sylvain Benner.

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

package org.jenkinsci.plugins.jobgenerator.parameters;

import hudson.Extension;
import hudson.model.*;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Default Generator Parameter which is a key value.
 * 
 * @author <a href="mailto:sylvain.benner@gmail.com">Sylvain Benner</a>
 */
public class GeneratorKeyValueParameterDefinition
                                           extends StringParameterDefinition {


    @DataBoundConstructor
    public GeneratorKeyValueParameterDefinition(String name,
                                               String defaultValue,
                                               String description) {
        super(name, defaultValue, description);
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(
            ParameterValue defaultValue) {
        if (defaultValue instanceof GeneratorKeyValueParameterValue) {
            GeneratorKeyValueParameterValue value =
                                (GeneratorKeyValueParameterValue) defaultValue;
            return new GeneratorKeyValueParameterDefinition(getName(),
                                                            value.value,
                                                            getDescription());
        } else {
            return this;
        }
    }

    @Override
    public StringParameterValue getDefaultParameterValue() {
        GeneratorKeyValueParameterValue v =
           new GeneratorKeyValueParameterValue(getName(),
                                               getDefaultValue(),
                                               getDescription());
        return v;
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.GeneratorKeyValueParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/plugin/jobgenerator/help-generatorparameter.html";
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        GeneratorKeyValueParameterValue value =
                       req.bindJSON(GeneratorKeyValueParameterValue.class, jo);
        value.setDescription(getDescription());
        return value;
    }

    public ParameterValue createValue(String value) {
        return new GeneratorKeyValueParameterValue(getName(),
                                                   value,
                                                   getDescription());
    }

}
