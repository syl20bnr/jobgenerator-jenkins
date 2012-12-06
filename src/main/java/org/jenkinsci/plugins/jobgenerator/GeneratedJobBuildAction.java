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

import hudson.model.Action;

/**
 * Summary for generated/updated job.
 * 
 * @author <a href="mailto:sylvain.benner@gmail.com">Sylvain Benner</a>
 */
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
