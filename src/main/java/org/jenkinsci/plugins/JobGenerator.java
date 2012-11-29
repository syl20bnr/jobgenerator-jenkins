/*
The MIT License

Copyright (c) 2012, Ubisoft Entertainment, Sylvain Benner.

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

package org.jenkinsci.plugins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue.FlyweightTask;
import hudson.model.labels.LabelAtom;
import hudson.scm.PollingResult;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.triggers.TimerTrigger.TimerTriggerCause;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;

import jenkins.util.TimeDuration;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import org.jenkinsci.plugins.GeneratorRun;

/**
 * Defines project which build action is to generates a new job configuration.
 *
 * @author <a href="mailto:sylvain.benner@gmail.com">Sylvain Benner</a>
 */
public class JobGenerator extends Project<JobGenerator, GeneratorRun>
                          implements TopLevelItem, FlyweightTask, SCMedItem {

    private transient boolean overwrite = false;
    private transient boolean delete = false;
    private transient boolean processAll = false;
    private transient boolean initiator = false;
    private String generatedJobName = "";
    private String generatedDisplayJobName = "";

    @DataBoundConstructor
    public JobGenerator(ItemGroup parent, String name) {
        super(parent, name);
    }

    public void doBuild(StaplerRequest req,
                        StaplerResponse rsp,
                        TimeDuration delay) throws IOException ,
                                                   ServletException {
        this.initiator = true;
        super.doBuild(req, rsp, delay);
    }

    @Override
    public boolean isParameterized() {
        // not working for now since doBuild method of jenkins core does not
        // use this method.
        return true;
    }

    /**
     * Returns plugin own parameter definition property containing only
     * template parameters.
     * It return always the parameter definition of the top most
     * project of the hierarchy.
     */
    @Override
    public <T extends JobProperty> T getProperty(Class<T> clazz) {
        T res = super.getProperty(clazz);
        if(ParametersDefinitionProperty.class == clazz){
            TemplateParametersDefinitionProperty newres = null;
            if(res != null){
                newres = new TemplateParametersDefinitionProperty(
                                           (ParametersDefinitionProperty) res);
                if(newres.getParameterDefinitions().isEmpty()){
                    newres = (TemplateParametersDefinitionProperty)
                                  this.getTopMostParameterDefinitionProperty();
                }
            }
            else{
                newres = (TemplateParametersDefinitionProperty)
                                  this.getTopMostParameterDefinitionProperty();
            }
            if(newres != null && !newres.getParameterDefinitions().isEmpty())
            {
                newres.setOwner2(this);
                return (T) newres;
            }
        }
        return res;
    }

    @Override
    public Label getAssignedLabel() { 
        return new LabelAtom("master");
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher,
            BuildListener listener, File changelogFile) throws IOException,
            InterruptedException {
        return true;
    }

    @Override
    public boolean schedulePolling() {
        return false;
    }

    @Override
    public PollingResult poll(TaskListener listener) {
        return PollingResult.NO_CHANGES;
    }

    @Override
    public boolean scheduleBuild(int quietPeriod, Cause c, Action... actions) {
        if(!SCMTriggerCause.class.isInstance(c) &&
           !TimerTriggerCause.class.isInstance(c)){
            return super.scheduleBuild(quietPeriod, c, actions);
        }
        return false;
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) 
            throws IOException, ServletException, FormException {
        super.submit(req, rsp);
        JSONObject json = req.getSubmittedForm();
        JSONObject o = json.getJSONObject(
                                     "plugin-jobgenerator-GeneratedJobConfig");
        if(o != null) {
            String k = "generatedJobName";
            if(o.has(k)){ this.generatedJobName = o.getString(k); }
            k = "generatedDisplayJobName";
            if(o.has(k)){ this.generatedDisplayJobName = o.getString(k); }
        }
    }

    @Extension
    public static final JobGeneratorDescriptor DESCRIPTOR = 
                                                  new JobGeneratorDescriptor();

    public JobGeneratorDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this,
                                             Messages.JobGenerator_Messages());
    }

    @Override
    protected Class<GeneratorRun> getBuildClass() {
        return GeneratorRun.class;
    }

    public JobProperty getTopMostParameterDefinitionProperty(){
        AbstractProject topmost = this;
        List<AbstractProject> lup = topmost.getUpstreamProjects();
        while(lup.isEmpty() == false){
            topmost = lup.get(0);
            lup = topmost.getUpstreamProjects();
        }
        if(topmost != this){
            return topmost.getProperty(ParametersDefinitionProperty.class);
        }
        return null;
    }

    public boolean isInitiator(){
        return this.initiator;
    }
    public void resetInitiator(){
        this.initiator = false;
    }

    public String getGeneratedJobName(){
        return this.generatedJobName;
    }
    public void setGeneratedJobName(String name){
        this.generatedJobName = name;
    }

    public String getGeneratedDisplayJobName(){
        return this.generatedDisplayJobName;
    }
    public void setGeneratedDisplayJobName(String name){
        this.generatedDisplayJobName = name;
    }

    public boolean getProcessAll(){
        return this.processAll;
    }
    public void setProcessAll(boolean check){
        this.processAll = check;
    }
    public boolean getOverwrite(){
        return this.overwrite;
    }
    public void setOverwrite(boolean check){
        this.overwrite = check;
    }
    public boolean getDelete(){
        return this.delete;
    }
    public void setDelete(boolean check){
        this.delete = check;
    }

    public void copyOptions(JobGenerator p){
        p.setOverwrite(this.getOverwrite());
        p.setDelete(this.getDelete());
        p.setProcessAll(this.getProcessAll());
    }

    private void gatherParameters(AbstractProject up,
                                  List<ParameterDefinition> lpd){
        ParametersDefinitionProperty uppdp = (ParametersDefinitionProperty)
                            up.getProperty(ParametersDefinitionProperty.class);
        List<ParameterDefinition> uplpd = uppdp.getParameterDefinitions();
        for(ParameterDefinition uppd: uplpd){
            if (TemplateKeyValueParameterDefinition.class.isInstance(uppd)){
                boolean found = false;
                for(ParameterDefinition pd: lpd){
                    if(pd.getName().equals(uppd.getName())){
                        found = true;
                    }
                }
                if(!found){
                    lpd.add(uppd);
                }
            }
        }
        List<AbstractProject> upProjects = up.getUpstreamProjects();
        for(AbstractProject upProject: upProjects){
            this.gatherParameters(upProject, lpd);
        }
    }

    public static class JobGeneratorDescriptor
                                           extends AbstractProjectDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.JobGenerator_Messages();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new JobGenerator(parent, name);
        }

        public FormValidation doCheckGeneratedJobName(
                @QueryParameter String name,
                @QueryParameter String value) {
            if (value.equals(name)) {
                return FormValidation.error("Generated job name must be " +
                                            "different than this job " +
                                            "generator name.");
            }
            return FormValidation.validateRequired(value);
        }

        public String getDefaultEntriesPage(){
            return getViewPage(FreeStyleProject.class,
                               "configure-entries.jelly");
        }
    }

}
