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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractList;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.util.TimeDuration;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.*;

import org.jenkinsci.plugins.jobgenerator.JobGenerator;

/**
 * Wrap Jenkins ParametersDefinitionProperty to be able to display its own
 * view.
 * 
 * @author <a href="mailto:sylvain.benner@gmail.com">Sylvain Benner</a>
 */
@ExportedBean(defaultVisibility=2)
public class GeneratorParametersDefinitionProperty
        extends ParametersDefinitionProperty 
        implements Action {

    private static final Logger LOGGER = Logger.getLogger(
                         GeneratorParametersDefinitionProperty.class.getName());

    private transient List<ParameterDefinition> generatorParameterDefinitions;
    private transient List<ParameterDefinition> globalParameterDefinitions;
    private transient List<ParameterDefinition> localParameterDefinitions;

    public GeneratorParametersDefinitionProperty(
            ParametersDefinitionProperty property,
            JobGenerator project) {
        this.owner = project;
        this.generatorParameterDefinitions = 
                                          new ArrayList<ParameterDefinition>();
        List<ParameterDefinition> lpd = property.getParameterDefinitions();
        for(ParameterDefinition pd: lpd){
            if (GeneratorKeyValueParameterDefinition.class.isInstance(pd) ||
                GeneratorChoiceParameterDefinition.class.isInstance(pd)){
                this.generatorParameterDefinitions.add(pd);
            }
        }
        this.globalParameterDefinitions = new ArrayList<ParameterDefinition>();
        this.localParameterDefinitions = new ArrayList<ParameterDefinition>();
    }

    // required since setOwner is protected.
    public void setOwner2(JobGenerator owner){
        this.owner = owner;
    }

    public void addGlobalParameters(List<ParameterDefinition> params){
        this.addParameters(params, this.globalParameterDefinitions);
    }
    public List<ParameterDefinition> getGlobalParameters(){
        return this.globalParameterDefinitions;
    }

    public void addLocalParameters(List<ParameterDefinition> params){
        this.addParameters(params, this.localParameterDefinitions);
    }
    public List<ParameterDefinition> getLocalParameters(){
        return this.localParameterDefinitions;
    }

    private void addParameters(List<ParameterDefinition> in,
                              List<ParameterDefinition> out){
        out.clear();
        for(ParameterDefinition pd: in){
            if(GeneratorKeyValueParameterDefinition.class.isInstance(pd) ||
               GeneratorChoiceParameterDefinition.class.isInstance(pd)) {
                out.add(pd);
            }
        }
    }

    @Override
    public void _doBuild(StaplerRequest req,
                         StaplerResponse rsp,
                         @QueryParameter TimeDuration delay)
                         throws IOException, ServletException {
        if(req.getMethod().equals("POST")) {
            JSONObject json = req.getSubmittedForm();
//            System.out.println(json);
            JSONArray a = JSONArray.fromObject(json.get("parameter"));
            for (Object o : a) {
                JSONObject jo = (JSONObject) o;
                String name = jo.getString("name");
                if(this.getParameterDefinition(name) == null){
                    String value = jo.getString("value");
                    GeneratorKeyValueParameterDefinition pdef = 
                        new GeneratorKeyValueParameterDefinition(name,
                                                                 value, "");
                    this.generatorParameterDefinitions.add(pdef);
                }
            }
            JobGenerator p = (JobGenerator)this.getOwner();
            JSONObject o = json.getJSONObject("overwrite");
            p.setOverwrite(!o.isNullObject());
            o = json.getJSONObject("delete");
            p.setDelete(false);
            if(!o.isNullObject()){
                p.setDelete(o.getBoolean("confirm"));
            }
            o = json.getJSONObject("processall");
            p.setProcessAll(!o.isNullObject());
        }
        super._doBuild(req, rsp, new TimeDuration(0));
    }

    @Exported
    @Override
    public List<ParameterDefinition> getParameterDefinitions() {
        return this.generatorParameterDefinitions;
    }

    @Override
    public List<String> getParameterDefinitionNames() {
        return new AbstractList<String>() {
            public String get(int index) {
                return generatorParameterDefinitions.get(index).getName();
            }

            public int size() {
                return (generatorParameterDefinitions.size());
            }
        };
    }

    @Override
    public ParameterDefinition getParameterDefinition(String name) {
        for (ParameterDefinition pd : this.generatorParameterDefinitions)
            if (pd.getName().equals(name))
                return pd;
        return null;
    }
}
