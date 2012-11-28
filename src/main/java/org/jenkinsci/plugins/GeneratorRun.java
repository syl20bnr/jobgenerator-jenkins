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

import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Text;
import org.dom4j.Visitor;
import org.dom4j.VisitorSupport;
import org.dom4j.io.SAXReader;

/**
 * Generates a configured job by copying this job config.xml and replacing
 * template parameters by replacing then with values provided by the user at
 * build launch time.
 * 
 * @author <a href="mailto:sylvain.benner@gmail.com">Sylvain Benner</a>
 */
@SuppressWarnings("rawtypes")
public class GeneratorRun extends Build<JobGenerator, GeneratorRun> {

    private static final Logger LOGGER = Logger.getLogger(
                                                 GeneratorRun.class.getName());

    public GeneratorRun(JobGenerator job, File buildDir)
            throws IOException {
        super(job, buildDir);
    }

    public GeneratorRun(JobGenerator job) throws IOException {
        super(job);
    }

    public JobGenerator getJobGenerator() {
        return project;
    }

    public static String expand(String s, List<ParametersAction> params) {
        for (ParametersAction p : params) {
            List<ParameterValue> values = p.getParameters();
            for (ParameterValue v : values) {
                String decorated = "${" + v.getName() + "}";
                while (s.contains(decorated)) {
                    s = s.replace(decorated, ((StringParameterValue) v).value);
                }
            }
        }
        return s;
    }

    public static String getExpandedJobName(JobGenerator p,
                                            List<ParametersAction> params){
        return expand(p.getGeneratedJobName(), params);
    }

    public String id(Run run) throws UnsupportedEncodingException {
        return URLEncoder.encode(run.getParent().getFullDisplayName()
                                         + run.getNumber(),
                                 "UTF-8");
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        this.execute(new GeneratorImpl());
    }

    protected class GeneratorImpl extends AbstractBuildExecution {

        public GeneratorImpl() {
        }

        protected Result doRun(BuildListener listener) throws Exception {
            JobGenerator job = getJobGenerator();
            List<ParametersAction> params = getBuild().getActions(
                                          hudson.model.ParametersAction.class);
            String expName = getExpandedJobName(job, params);
            if(job.getDelete()){
                TopLevelItem i = Jenkins.getInstance().getItem(expName);
                if(i != null){
                    i.delete();
                    LOGGER.info(String.format("Deleted job %s", expName));
                }
                else{
                    LOGGER.info(String.format("Job %s does not exist. " +
                                              "Skip delete operation.",
                                              expName));
                }
            }
            else{
                if(Jenkins.getInstance().getItem(expName) != null &&
                   !job.getOverwrite()){
                    this.listener.error("The job %s already exists and the " +
                                        "overwrite option was not enabled.",
                                        expName);
                    return Result.FAILURE;
                }
                String expDispName = expand(
                        job.getGeneratedDisplayJobName(), params);
                File d = new File(job.getRootDir() +
                                  File.separator + ".." + File.separator +
                                  expName);
                if (!d.exists() && !d.mkdir()) {
                    return Result.FAILURE;
                }
                SAXReader reader = new SAXReader();
                Document doc = reader.read(
                                  job.getConfigFile().getFile());
                // Update root element
                Element root = doc.getRootElement();
                root.setName("project");
                root.remove(root.attribute("plugin"));
                // Update Display Name
                Node dispName = doc.selectSingleNode("//displayName");
                if(!expDispName.isEmpty()){
                    if(dispName != null){
                        dispName.setText(expDispName);
                    }
                    else{
                        root.addElement("displayName").addText(expDispName);
                    }
                }
                // Remove info specific to Job Generator
                List list = doc.selectNodes("//org.jenkinsci.plugins." + 
                                        "TemplateKeyValueParameterDefinition");
                for (Iterator iter = list.iterator(); iter.hasNext(); ) {
                    Node node = (Node) iter.next();
                    node.detach();
                }
                this.removeNodeIfEmpty(doc, "parameterDefinitions");
                this.removeNodeIfEmpty(doc,
                                  "hudson.model.ParametersDefinitionProperty");
                this.removeNodeIfEmpty(doc, "properties");
                this.removeNodeIfEmpty(doc, "generatedJobName");
                this.removeNodeIfEmpty(doc, "generatedDisplayJobName");
                // Expand Vars
                Visitor v = new ExpandVarsVisitor(params);
                doc.accept(v);
                v = new UpdateProjectReferencesVisitor(
                              job.getUpstreamProjects(), params);
                doc.accept(v);
                v = new UpdateProjectReferencesVisitor(
                            job.getDownstreamProjects(), params);
                doc.accept(v);
                // Create/Update Job
                doc.normalize();
                InputStream is = new ByteArrayInputStream(
                                                doc.asXML().getBytes("UTF-8")); 
                if(Jenkins.getInstance().getItem(expName) != null ){
                    LOGGER.info(String.format("Updated configuration of " +
                                              "job %s", expName));
                }
                else{
                    LOGGER.info(String.format("Created job %s", expName));
                }
                Jenkins.getInstance().createProjectFromXML(expName, is);
            }
            return Result.SUCCESS;
        }

        /**
         * Execute all downstream projects and pass template parameters to them.
         * Only the initiator of the build can schedule the build of the
         * downstream project.
         */
        @Override
        public void post2(BuildListener listener) throws Exception {
            JobGenerator job = getJobGenerator();
            Set<AbstractProject> sdp = new HashSet<AbstractProject>();
            this.gatherAllDownstreamProjects(job, sdp);
            List<ParametersAction> params = getBuild().getActions(
                                          hudson.model.ParametersAction.class);
            if(job.isInitiator() && job.getProcessAll()){
                for (AbstractProject p : sdp) {
                    Cause.UpstreamCause cause = new Cause.UpstreamCause(
                                                                   getBuild());
                    job.copyOptions((JobGenerator) p);
                    p.scheduleBuild2(1, cause, params);
                }
                job.resetInitiator();
            }
        }

        @Override
        public void cleanUp(BuildListener listener) throws Exception {
        }

        private void removeNodeIfEmpty(Document doc, String elem) {
            List list = doc.selectNodes("//" + elem);
            for (Iterator iter = list.iterator(); iter.hasNext(); ) {
                Node node = (Node) iter.next();
                if(node.selectNodes("./*").isEmpty()){
                    node.detach();
                }
            }
        }

        private void gatherAllDownstreamProjects(AbstractProject p,
                                                 Set<AbstractProject> acc){
            List<AbstractProject> ldp = p.getDownstreamProjects();
            for(AbstractProject dp: ldp){
                acc.add(dp);
                this.gatherAllDownstreamProjects(dp, acc);
            }
        }
    }

    class ExpandVarsVisitor extends VisitorSupport {

        private final List<ParametersAction> params;

        public ExpandVarsVisitor(List<ParametersAction> params){
            this.params = params;
        }
    
        @Override
        public void visit(Text node){
            node.setText(GeneratorRun.expand(node.getText(), this.params));
        }
    }

    class UpdateProjectReferencesVisitor extends VisitorSupport {

        private final List<JobGenerator> upanddownstreamprojects;
        private final List<ParametersAction> params;

        public UpdateProjectReferencesVisitor(
                List<AbstractProject> downstreamprojects,
                List<ParametersAction> params){
            this.upanddownstreamprojects = 
                                        new ArrayList<JobGenerator>();
            for(AbstractProject p: downstreamprojects){
                if(JobGenerator.class.isInstance(p)){
                    this.upanddownstreamprojects.add((JobGenerator)p);
                }
            }
            this.params = params;
        }
    
        @Override
        public void visit(Text node){
            String expanded = "";
            for(String s: node.getText().split(",")){
                for(JobGenerator p: this.upanddownstreamprojects){
                   if(s.equals(p.getName())){
                       if(!expanded.isEmpty()){
                           expanded += ",";
                       }
                       expanded += GeneratorRun.getExpandedJobName(p, params);
                   }
                }
            }
            if(!expanded.isEmpty()){
                node.setText(expanded);
            }
        }
    }
}
