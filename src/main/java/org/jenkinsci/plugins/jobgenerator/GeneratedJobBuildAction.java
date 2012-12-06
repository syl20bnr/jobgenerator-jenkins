package org.jenkinsci.plugins.jobgenerator;

import hudson.model.Action;

class GeneratedJobBuildAction implements Action {
    public final String jobName;
    public final boolean created;

    public GeneratedJobBuildAction(String job, boolean created) {
        this.jobName = job;
        this.created = created;
    }

    /**
     * No task list item.
     */
    public String getIconFileName() {
       return null;
    }

    public String getDisplayName() {
        return "Generated Job";
    }

    public String getUrlName() {
        return "generated_job";
    }

    public String getJob() {
        return this.jobName;
    }

    public boolean getCreated(){
        return this.created;
    }

}
