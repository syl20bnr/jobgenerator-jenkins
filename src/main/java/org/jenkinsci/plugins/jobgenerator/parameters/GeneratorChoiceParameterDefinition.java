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

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.apache.commons.lang.StringUtils;
import net.sf.json.JSONObject;
import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.ParameterDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * Choice for predefined Generator Parameter.
 * 
 * @author <a href="mailto:sylvain.benner@gmail.com">Sylvain Benner</a>
 * @author Original code from huybrechts
 */
public class GeneratorChoiceParameterDefinition
        extends SimpleParameterDefinition {
    private final List<String> choices;
    private final String defaultValue;

    @DataBoundConstructor
    public GeneratorChoiceParameterDefinition(String name,
                                              String choices,
                                              String description) {
        super(name, description);
        this.choices = Arrays.asList(choices.split("\\r?\\n"));
        if (choices.length()==0) {
            throw new IllegalArgumentException("No choices found");
        }
        defaultValue = null;
    }

    public GeneratorChoiceParameterDefinition(String name,
                                              String[] choices,
                                              String description) {
        super(name, description);
        this.choices = new ArrayList<String>(Arrays.asList(choices));
        if (this.choices.isEmpty()) {
            throw new IllegalArgumentException("No choices found");
        }
        defaultValue = null;
    }

    private GeneratorChoiceParameterDefinition(String name,
                                               List<String> choices,
                                               String defaultValue,
                                               String description) {
        super(name, description);
        this.choices = choices;
        this.defaultValue = defaultValue;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(
            ParameterValue defaultValue) {
        if (defaultValue instanceof GeneratorKeyValueParameterValue) {
            GeneratorKeyValueParameterValue value =
                                (GeneratorKeyValueParameterValue) defaultValue;
            return new GeneratorChoiceParameterDefinition(getName(),
                                       choices, value.value, getDescription());
        } else {
            return this;
        }
    }
    
    @Exported
    public List<String> getChoices() {
        return choices;
    }

    public String getChoicesText() {
        return StringUtils.join(choices, "\n");
    }

    @Override
    public GeneratorKeyValueParameterValue getDefaultParameterValue() {
        return new GeneratorKeyValueParameterValue(getName(),
                         defaultValue == null ? choices.get(0) : defaultValue,
                         getDescription());
    }

    private GeneratorKeyValueParameterValue checkValue(
            GeneratorKeyValueParameterValue value) {
        if (!choices.contains(value.value))
            throw new IllegalArgumentException("Illegal choice: " +
                                               value.value);
        return value;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        GeneratorKeyValueParameterValue value =
                       req.bindJSON(GeneratorKeyValueParameterValue.class, jo);
        value.setDescription(getDescription());
        return checkValue(value);
    }

    public GeneratorKeyValueParameterValue createValue(String value) {
        return checkValue(new GeneratorKeyValueParameterValue(getName(),
                                                     value, getDescription()));
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.GeneratorChoiceParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/plugin/jobgenerator/help-generatorchoiceparameter.html";
        }
    }
 }

