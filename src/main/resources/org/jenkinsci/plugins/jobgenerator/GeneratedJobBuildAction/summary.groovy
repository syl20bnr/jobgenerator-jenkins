package org.jenkinsci.plugins.jobgenerator;

import lib.LayoutTagLib

l=namespace(LayoutTagLib)
t=namespace("/lib/hudson")
st=namespace("jelly:stapler")
f=namespace("lib/form")

t.summary(icon:"folder.png") {
    if(my?.created) {
        raw("Created Job:")
    }
    else {
        raw("Updated Job:")
    }
    ul(class:"jobList") {
        li() {
            a(href:"${rootURL}/job/${my?.jobName}/",
                                 class:"model-link tl-tr") { raw(my?.jobName) }
        }
    }
}
