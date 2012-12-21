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

package org.jenkinsci.plugins.jobgenerator;

import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameters;
import hudson.plugins.parameterizedtrigger.BuildTrigger;
import hudson.plugins.parameterizedtrigger.BuildTriggerConfig;
import hudson.util.XStream2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.apache.tools.ant.filters.StringInputStream;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Text;
import org.dom4j.Visitor;
import org.dom4j.VisitorSupport;
import org.dom4j.io.SAXReader;

import org.jenkins_ci.plugins.run_condition.RunCondition;
import org.jenkinsci.plugins.jobgenerator.actions.*;
import org.jenkinsci.plugins.jobgenerator.parameters.*;

/**
 * Generates a configured job by copying this job config.xml and replacing
 * generator parameters with values provided by the user at build time.
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
        // check existenz of variables to replace
        boolean proceed = false;
        for (ParametersAction p : params) {
            List<ParameterValue> values = p.getParameters();
            for (ParameterValue v : values) {
                String decorated = "${" + v.getName() + "}";
                if (s.contains(decorated)) {
                    proceed = true;
                    break;
                }
            }
        }
        if (proceed){
            for (ParametersAction p : params) {
                List<ParameterValue> values = p.getParameters();
                for (ParameterValue v : values) {
                    s = GeneratorRun.expand(s, v.getName(), 
                                  ((GeneratorKeyValueParameterValue) v).value);
                }
            }
            // replace nested variables
            s = expand(s, params);
        }
        return s;
    }

    private static String expand(String s, String n, String v){
        String decorated = "${" + n + "}";
        while (s.contains(decorated)) {
            s = s.replace(decorated, v);
        }
        return s;
    }

    public static String getExpandedJobName(JobGenerator p,
                                            List<ParametersAction> params){
        return expand(p.getGeneratedJobName(), params);
    }

    public static boolean allParametersAreResolved(Element root){
        List<String> enames = new ArrayList<String>();
        enames.add("arg1");
        enames.add("arg2");
        enames.add("expression");
        enames.add("label");
        for(String s: enames){
            Element e = (Element) root.selectSingleNode("/" + root.getName() +
                                                        "/*/" + s);
            if (e != null){
                String t = e.getText();
                if (t.contains("${") && t.contains("}")){
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isEvaluationSupported(Element root){
        // TODO need to find a better way to detect if we can evaluate the
        // expression for a given conditionnal class
        List<String> notSupportedClasses = new ArrayList<String>();
        notSupportedClasses.add("AlwaysRun");
        notSupportedClasses.add("NeverRun");
        notSupportedClasses.add("CauseCondition");
        notSupportedClasses.add("StatusCondition");
        notSupportedClasses.add("DayCondition");
        notSupportedClasses.add("ShellCondition");
        notSupportedClasses.add("BatchFileCondition");
        notSupportedClasses.add("FileExistsCondition");
        notSupportedClasses.add("FilesMatchCondition");
        notSupportedClasses.add("TimeCondition");
        if(root.attribute("class") != null){
            String name = root.attributeValue("class");
            for(String nname: notSupportedClasses){
                if(name.contains(nname)){
                    return false;
                }
            }
        }
        List children = root.elements();
        for (Iterator i = children.iterator(); i.hasNext();) {
            Element e = (Element) i.next();
            return isEvaluationSupported(e);
        }
        return true;
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
            // TODO syl20bnr: This function is a big mess. I plan to
            // refactoring it when it comes to add some tests.
            if(!this.checkParameters(listener)){
                return Result.FAILURE;
            }
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
                // Expand Vars
                Visitor v = new ExpandVarsVisitor(params);
                doc.accept(v);
                v = new UpdateProjectReferencesVisitor(
                              job.getUpstreamProjects(), params);
                doc.accept(v);
                v = new UpdateProjectReferencesVisitor(
                            job.getDownstreamProjects(), params);
                doc.accept(v);
                // Remove info specific to Job Generator
                v = new GatherElementsToRemoveVisitor();
                doc.accept(v);
                for(Element e: ((GatherElementsToRemoveVisitor)v).toRemove){
                    e.detach();
                }
                this.removeNodeIfNoChild(doc, "parameterDefinitions");
                this.removeNodeIfNoChild(doc,
                                  "hudson.model.ParametersDefinitionProperty");
                this.removeNodeIfNoChild(doc, "generatedJobName");
                this.removeNodeIfNoChild(doc, "generatedDisplayJobName");
                // Evaluate builders (Single step)
                List vroots = doc.selectNodes("//org.jenkinsci.plugins." +
                   "conditionalbuildstep.singlestep.SingleConditionalBuilder");
                for (Iterator i = vroots.iterator(); i.hasNext();) {
                    Element vroot = (Element) i.next();
                    v = new EvaluateBuildersSingleVisitor(vroot,
                                               (AbstractBuild<?, ?>)getBuild(),
                                               listener);
                    vroot.accept(v);
                    for (Element e: ((EvaluateBuildersSingleVisitor)v).toAdd){
                        List siblings = vroot.getParent().elements();
                        siblings.add(siblings.indexOf(vroot), e);
                    }
                    for (Element e:
                         ((EvaluateBuildersSingleVisitor)v).toRemove) {
                        e.detach();
                    }
                }
                // Evaluate builders (Multiple steps)
                vroots = doc.selectNodes("//org.jenkinsci.plugins." +
                                    "conditionalbuildstep.ConditionalBuilder");
                for (Iterator i = vroots.iterator(); i.hasNext();) {
                    Element vroot = (Element) i.next();
                    v = new EvaluateBuildersMultiVisitor(vroot,
                                               (AbstractBuild<?, ?>)getBuild(),
                                               listener);
                    vroot.accept(v);
                    for (Element e: ((EvaluateBuildersMultiVisitor)v).toAdd){
                        List siblings = vroot.getParent().elements();
                        siblings.add(siblings.indexOf(vroot), e);
                    }
                    for (Element e:
                         ((EvaluateBuildersMultiVisitor)v).toRemove) {
                        e.detach();
                    }
                }
                // Evaluate publishers
                Element flexroot = (Element) doc.selectSingleNode(
                                "//org.jenkins__ci.plugins." +
                                "flexible__publish.FlexiblePublisher");
                if(flexroot != null){
                    vroots = doc.selectNodes("//org.jenkins__ci.plugins." +
                                     "flexible__publish.ConditionalPublisher");
                    for (Iterator i = vroots.iterator(); i.hasNext();) {
                        Element vroot = (Element) i.next();
                        v = new EvaluatePublishersVisitor(vroot,
                                               (AbstractBuild<?, ?>)getBuild(),
                                               listener);
                        vroot.accept(v);
                        for (Element e: ((EvaluatePublishersVisitor)v).toAdd){
                            List siblings = flexroot.getParent().elements();
                            siblings.add(siblings.indexOf(flexroot), e);
                        }
                        for (Element e:
                             ((EvaluatePublishersVisitor)v).toRemove) {
                            e.detach();
                        }
                    }
                    this.removeNodeIfNoChild(flexroot, "publishers");
                    this.removeNodeIfNoChild(doc, "org.jenkins__ci.plugins." +
                                        "flexible__publish.FlexiblePublisher");
                }
                // Create/Update Job
                doc.normalize();
                InputStream is = new ByteArrayInputStream(
                                                doc.asXML().getBytes("UTF-8"));
//                System.out.println(doc.asXML());
                boolean created =
                    Jenkins.getInstance().getItem(expName) == null;
                if(created){
                    LOGGER.info(String.format("Created job %s", expName));
                }
                else{
                    LOGGER.info(String.format("Updated configuration of " +
                                              "job %s", expName));
                }
                Jenkins.getInstance().createProjectFromXML(expName, is);
                // save generated job name
                GeneratedJobBuildAction action =
                                 new GeneratedJobBuildAction(expName, created);
                getBuild().addAction(action);
            }
            return Result.SUCCESS;
        }

        @Override
        public void post2(BuildListener listener) throws Exception {
        }

        /**
         * Execute all downstream projects and pass template parameters to them.
         * Only the initiator of the build can schedule the build of the
         * downstream project.
         */
        @Override
        public void cleanUp(BuildListener listener) throws Exception {
            JobGenerator job = getJobGenerator();
            if(!job.getProcessAll()){
                return;
            }
            List<ParametersAction> lpa = getBuild().getActions(
                                          hudson.model.ParametersAction.class);
            BuildTrigger bt = job.getPublishersList().get(BuildTrigger.class);
            if (bt != null) {
                // parameterized build trigger
                for (ListIterator<BuildTriggerConfig> btc =
                        bt.getConfigs().listIterator(); btc.hasNext();) {
                    BuildTriggerConfig c = btc.next();
                    for (AbstractProject p : c.getProjectList(job.getParent(),
                                                              null)) {
                        List<ParametersAction> importParams =
                                             new ArrayList<ParametersAction>();
                        importParams.addAll(lpa);
                        List<AbstractBuildParameters> lbp = c.getConfigs();
                        for(AbstractBuildParameters bp: lbp){
                            if(bp.getClass().getSimpleName().equals(
                                          "GeneratorKeyValueBuildParameters")){
                                importParams.add((ParametersAction)
                                    bp.getAction(GeneratorRun.this, listener));
                            }
                        }
                        job.copyOptions((JobGenerator) p);
                        Cause.UpstreamCause cause = new Cause.UpstreamCause(
                                                                   getBuild());
                        p.scheduleBuild2(0, cause, importParams);
                    }
                }
            }
            else{
                // standard Jenkins dependencies
                for(AbstractProject dp: job.getDownstreamProjects()){
                    Cause.UpstreamCause cause = new Cause.UpstreamCause(
                                                                   getBuild());
                    job.copyOptions((JobGenerator) dp);
                    dp.scheduleBuild2(0, cause, lpa);
                }
            }
        }

        private void removeNodeIfNoChild(Node root, String elem) {
            List list = root.selectNodes("//" + elem);
            for (Iterator iter = list.iterator(); iter.hasNext(); ) {
                Node node = (Node) iter.next();
                if(node.selectNodes("./*").isEmpty()){
                    node.detach();
                }
            }
        }

        private boolean checkParameters(BuildListener listener) {
            JobGenerator job = getJobGenerator();
            List<ParametersAction> params = getBuild().getActions(
                                          hudson.model.ParametersAction.class);
            String expName = getExpandedJobName(job, params);
            if(job.getGeneratedJobName().isEmpty()){
                listener.error("Generated Project Name cannot be empty. " +
                               "Please review the configuration of the " +
                               "project.");
                return false;
            }
            else if(job.getName().equals(expName)){
                listener.error("Generated Project Name cannot be equal " +
                               "to the Job Generator name. " +
                               "Please review the configuration of the " +
                               "project.");
                return false;
            }
            else{
                // check if the expanded name correspond to another job
                // generator
                TopLevelItem i = Jenkins.getInstance().getItem(expName);
                if(i != null){
                    if(JobGenerator.class.isInstance(i)){
                        listener.error("Generated Project Name corresponds " +
                                       "to a the Job Generator " +
                                       i.getName() + 
                                       ". Generation has been aborted to " +
                                       "prevent any loss of data.");
                return false;
                    }
                }
            }

            return true;
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
        private final List<JobGenerator> upordownstreamprojects;
        private final List<ParametersAction> params;
        private Set<Entry<Object, Object>> passedVariables = null;

        public UpdateProjectReferencesVisitor(
                List<AbstractProject> downstreamprojects,
                List<ParametersAction> params){
            this.upordownstreamprojects = 
                                        new ArrayList<JobGenerator>();
            for(AbstractProject p: downstreamprojects){
                if(JobGenerator.class.isInstance(p)){
                    this.upordownstreamprojects.add((JobGenerator)p);
                }
            }
            this.params = params;
        }

        @Override
        public void visit(Element node) {
            String n = node.getName();
            if(n.equals("properties") &&
               node.getParent().getName().contains("org.jenkinsci.plugins." +
                                       "jobgenerator.parameterizedtrigger." +
                                          "GeneratorKeyValueBuildParameters")){
                // harvest variables passed by the parameterized trigger plugin
                String t = node.getText();
                if(t.contains("=")){
                    Properties p = new Properties();
                    try {
                        p.load(new StringInputStream(t));
                    }
                    catch (IOException e) {
                        return;
                    }
                    this.passedVariables = p.entrySet();
                }
            }
            else if(n.equals("triggerWithNoParameters")){
                // force trigger without any parameter
                node.setText("true");
            }
        }

        @Override
        public void visit(Text node){
            String expanded = "";
            for(String s: node.getText().split(",")){
                s = Util.fixEmptyAndTrim(s);
                if(s != null){
                    for(JobGenerator p: this.upordownstreamprojects){
                       if(s.equals(p.getName())){
                           String jexp = GeneratorRun.getExpandedJobName(p,
                                                                       params);
                           if(this.passedVariables != null){
                               // replace additional variables from the
                               // parameterized trigger plugin
                               for (Map.Entry<Object, Object> v :
                                                        this.passedVariables) {
                                   jexp = GeneratorRun.expand(jexp,
                                                      v.getKey().toString(),
                                                      v.getValue().toString());
                               }
                           }
                           if(!expanded.isEmpty()){
                               expanded += ",";
                           }
                           expanded += jexp;
                       }
                    }
                }
            }
            if(!expanded.isEmpty()){
                node.setText(expanded);
            }
        }
    }

    class GatherElementsToRemoveVisitor extends VisitorSupport {
        public List<Element> toRemove = new ArrayList<Element>();

        public GatherElementsToRemoveVisitor(){}

        @Override
        public void visit(Element node) {
            String n = node.getName();
            if(n.contains("GeneratorKeyValueParameterDefinition") ||
               n.contains("GeneratorChoiceParameterDefinition") ||
               n.contains("GeneratorKeyValueBuildParameters") ||
               n.contains("GeneratorCurrentParameters")){
                this.toRemove.add(node);
            }
        }
    }

    class EvaluateBuildersSingleVisitor extends VisitorSupport {
        private final Element root;
        private final AbstractBuild<?, ?> build;
        private final BuildListener listener;
        public List<Element> toAdd;
        public List<Element> toRemove;
        public EvaluateBuildersSingleVisitor(
                Element root,
                AbstractBuild<?, ?> build,
                BuildListener listener){
            this.root = root;
            this.build = build;
            this.listener = listener;
            this.toAdd = new ArrayList<Element>();
            this.toRemove = new ArrayList<Element>();
        }

        @Override
        public void visit(Element node) {
            String n = node.getName();
            if (n.equals("condition") && node.attribute("plugin") != null &&
                GeneratorRun.isEvaluationSupported(node) &&
                GeneratorRun.allParametersAreResolved(node)){
                // convert this chunk of xml config to a file
                InputStream is;
                try {
                    is = new ByteArrayInputStream(
                                               node.asXML().getBytes("UTF-8"));
                    XStream2 xs = new XStream2();
                    RunCondition rc = (RunCondition) xs.fromXML(is);
                    if(rc.runPerform(this.build, listener)){
                        Element builder =
                            (Element)this.root.selectSingleNode("buildStep");
                        Element ne = builder.createCopy();
                        ne.setName(ne.attributeValue("class"));
                        ne.attribute("class").detach();
                        this.toAdd.add(ne);
                    }
                    this.toRemove.add(this.root);
                } catch (UnsupportedEncodingException e) {
                } catch (FileNotFoundException e) {
                } catch (IOException e) {
                } catch (Exception e) {
                }
            }
        }
    }

    class EvaluateBuildersMultiVisitor extends VisitorSupport {
        private final Element root;
        private final AbstractBuild<?, ?> build;
        private final BuildListener listener;
        public List<Element> toAdd;
        public List<Element> toRemove;
        public EvaluateBuildersMultiVisitor(
                Element root,
                AbstractBuild<?, ?> build,
                BuildListener listener){
            this.root = root;
            this.build = build;
            this.listener = listener;
            this.toAdd = new ArrayList<Element>();
            this.toRemove = new ArrayList<Element>();
        }

        @Override
        public void visit(Element node) {
            String n = node.getName();
            if (n.equals("runCondition") && node.attribute("plugin") != null &&
                GeneratorRun.isEvaluationSupported(node) &&
                GeneratorRun.allParametersAreResolved(node)){
                try {
                    InputStream is = new ByteArrayInputStream(
                                               node.asXML().getBytes("UTF-8"));
                    XStream2 xs = new XStream2();
                    RunCondition rc = (RunCondition) xs.fromXML(is);
                    if(rc.runPerform(this.build, listener)){
                        Element broot = (Element)this.root.selectSingleNode(
                                                      "conditionalbuilders");
                        if (broot != null){
                            List builders = broot.elements();
                            for (Iterator i = builders.iterator();
                                                                i.hasNext();) {
                                Element b = (Element) i.next();
                                Element ne = b.createCopy();
                                this.toAdd.add(ne);
                            }
                        }
                    }
                    this.toRemove.add(this.root);
                } catch (UnsupportedEncodingException e) {
                } catch (FileNotFoundException e) {
                } catch (IOException e) {
                } catch (Exception e) {
                }
            }
        }
    }


    class EvaluatePublishersVisitor extends VisitorSupport {
        private final Element root;
        private final AbstractBuild<?, ?> build;
        private final BuildListener listener;
        public List<Element> toAdd;
        public List<Element> toRemove;
        public EvaluatePublishersVisitor(
                Element root,
                AbstractBuild<?, ?> build,
                BuildListener listener){
            this.root = root;
            this.build = build;
            this.listener = listener;
            this.toAdd = new ArrayList<Element>();
            this.toRemove = new ArrayList<Element>();
        }

        @Override
        public void visit(Element node) {
            String n = node.getName();
            if (n.equals("condition") && node.attribute("plugin") != null &&
                GeneratorRun.isEvaluationSupported(node) &&
                GeneratorRun.allParametersAreResolved(node)){
                // convert this chunk of xml config to a file
                InputStream is;
                try {
                    is = new ByteArrayInputStream(
                                               node.asXML().getBytes("UTF-8"));
                    XStream2 xs = new XStream2();
                    RunCondition rc = (RunCondition) xs.fromXML(is);
                    if(rc.runPerform(this.build, listener)){
                        Element builder =
                            (Element)this.root.selectSingleNode("publisher");
                        Element ne = builder.createCopy();
                        ne.setName(ne.attributeValue("class"));
                        ne.attribute("class").detach();
                        this.toAdd.add(ne);
                    }
                    this.toRemove.add(this.root);
                } catch (UnsupportedEncodingException e) {
                } catch (FileNotFoundException e) {
                } catch (IOException e) {
                } catch (Exception e) {
                }
            }
        }
    }
}
