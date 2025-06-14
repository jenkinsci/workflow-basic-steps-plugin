package org.jenkinsci.plugins.workflow.steps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.httpclient.RobustHTTPClient;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ArtifactUnarchiverStepExecution extends SynchronousNonBlockingStepExecution<List<FilePath>> {

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
    protected List<FilePath> run() throws Exception {
        // where to copy artifacts from?
        Run<?, ?> r = getContext().get(Run.class); // TODO consider an option to override this (but in what format?)
        TaskListener listener = getContext().get(TaskListener.class);

        ArtifactManager am = r.getArtifactManager();

        List<FilePath> files = new ArrayList<>();

        for (Entry<String, String> e : mapping.entrySet()) {
            FilePath dst = new FilePath(getContext().get(FilePath.class), e.getValue());
            String src = e.getKey();
            Collection<String> all = am.root().list(src.replace('\\', '/'), null, true);
            if (all.isEmpty()) {
                throw new AbortException("no artifacts to unarchive in " + src);
            } else if (all.size() == 1 && all.stream().findFirst().get().equals(src)) {
                final String firstElement = all.stream().findFirst().get();
                // the source is a file
                if (dst.isDirectory()) {
                    dst = dst.child(getFileName(firstElement));
                }

                files.add(copy(am.root().child(firstElement), dst, listener));
            } else {
                // copy into a directory
                for (String path : all) {
                    files.add(copy(am.root().child(path), dst.child(path), listener));
                }
            }
        }

        return files;
    }

    private FilePath copy(VirtualFile src, FilePath dst, TaskListener listener) throws IOException, InterruptedException {
        URL u = src.toExternalURL();
        if (u != null) {
            new RobustHTTPClient().copyFromRemotely(dst, u, listener);
        } else {
            try (InputStream in = src.open()) {
                dst.copyFrom(in);
            }
        }
        return dst;
    }

    /**
     * Grabs the file name portion out of a path name.
     */
    private String getFileName(String s) {
        int idx = s.lastIndexOf('/');
        if (idx>=0) s=s.substring(idx+1);
        idx = s.lastIndexOf('\\');
        if (idx>=0) s=s.substring(idx+1);
        return s;
    }

    private static final long serialVersionUID = 1L;

}
