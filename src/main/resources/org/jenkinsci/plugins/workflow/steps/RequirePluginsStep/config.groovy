package org.jenkinsci.plugins.workflow.steps.RequirePluginsStep
f = namespace(lib.FormTagLib)
f.entry(field: 'plugins', title: 'Required plugins, one per line') {
    f.textarea(value: instance == null ? '' : instance.plugins.join('\n'))
}
