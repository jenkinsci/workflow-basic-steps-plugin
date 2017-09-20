package org.jenkinsci.plugins.workflow.steps;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


import jenkins.MasterToSlaveFileCallable;
import jenkins.util.BuildListenerAdapter;

/**
 * @author Kohsuke Kawaguchi
 */
public class ArtifactArchiverStepExecution extends SynchronousNonBlockingStepExecution<Void> {

    private transient final ArtifactArchiverStep step;

    ArtifactArchiverStepExecution(ArtifactArchiverStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    protected Void run() throws Exception {
        FilePath ws = getContext().get(FilePath.class);
        ws.mkdirs();
        TaskListener listener = getContext().get(TaskListener.class);
        if (listener != null) {
            listener.getLogger().println(Messages.ArtifactArchiverStepExecution_Deprecated());
        }
        Map<String,String> files = ws.act(new ListFiles(step.getIncludes(), step.getExcludes()));
        getContext().get(Run.class).pickArtifactManager().archive(ws, getContext().get(Launcher.class), new BuildListenerAdapter(getContext().get(TaskListener.class)), files);
        return null;
    }

    private static final class ListFiles extends MasterToSlaveFileCallable<Map<String,String>> {
        private static final long serialVersionUID = 1;
        private final String includes, excludes;
        ListFiles(String includes, String excludes) {
            this.includes = includes;
            this.excludes = excludes;
        }
        @Override public Map<String,String> invoke(File basedir, VirtualChannel channel) throws IOException, InterruptedException {
            Map<String,String> r = new HashMap<>();
            for (String f : Util.createFileSet(basedir, includes, excludes).getDirectoryScanner().getIncludedFiles()) {
                f = f.replace(File.separatorChar, '/');
                r.put(f, f);
            }
            return r;
        }
    }

    private static final long serialVersionUID = 1L;

}
