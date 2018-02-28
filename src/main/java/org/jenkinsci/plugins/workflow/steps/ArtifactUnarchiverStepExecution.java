package org.jenkinsci.plugins.workflow.steps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import jenkins.MasterToSlaveFileCallable;
import jenkins.util.VirtualFile;

public class ArtifactUnarchiverStepExecution extends SynchronousNonBlockingStepExecution<Void> {

    @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
    private transient final Map<String,String> mapping;

    ArtifactUnarchiverStepExecution(Map<String, String> mapping, StepContext context) throws Exception {
        super(context);
        if (mapping == null) {
            throw new AbortException("'mapping' has not been defined for this 'unarchive' step");
        }
        this.mapping = mapping;
    }

    @Override
    protected Void run() throws Exception {
        // where to copy artifacts from?
        Run<?, ?> r = getContext().get(Run.class); // TODO consider an option to override this (but in what format?)

        VirtualFile root = r.getArtifactManager().root();
        FilePath target = getContext().get(FilePath.class);
        if (target.isRemote()) {
            VirtualFile rootR = root.asRemotable();
            if (rootR != null) {
                return target.act(new Copy(mapping, target, rootR));
            } else {
                return new Copy(mapping, target, root).invoke(null, null);
            }
        } else {
            return new Copy(mapping, target, root).invoke(null, null);
        }
    }

    private static class Copy extends MasterToSlaveFileCallable<Void> {

        private static final long serialVersionUID = 1L;

        private final Map<String, String> _mapping;
        private final FilePath target;
        private final VirtualFile root;

        Copy(Map<String, String> _mapping, FilePath target, VirtualFile root) {
            this._mapping = _mapping;
            this.target = target;
            this.root = root;
        }

        @Override public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            for (Entry<String, String> e : _mapping.entrySet()) {
                FilePath dst = new FilePath(target, e.getValue());
                String src = e.getKey();
                Collection<String> all = root.list(src, null, false);
                if (all.isEmpty()) {
                    throw new AbortException("no artifacts to unarchive in " + src);
                } else if (all.size() == 1 && all.iterator().next().equals(src)) {
                    // the source is a file
                    if (dst.isDirectory()) {
                        dst = dst.child(getFileName(all.iterator().next()));
                    }

                    copy(root.child(all.iterator().next()), dst);
                } else {
                    // copy into a directory
                    for (String path : all) {
                        copy(root.child(path), dst.child(path));
                    }
                }
            }
            return null;
        }

    }

    private static void copy(VirtualFile src, FilePath dst) throws IOException, InterruptedException {
        try (InputStream in = src.open()) {
            dst.copyFrom(in);
        }
    }

    /**
     * Grabs the file name portion out of a path name.
     */
    private static String getFileName(String s) {
        int idx = s.lastIndexOf('/');
        if (idx>=0) s=s.substring(idx+1);
        idx = s.lastIndexOf('\\');
        if (idx>=0) s=s.substring(idx+1);
        return s;
    }

    private static final long serialVersionUID = 1L;

}
