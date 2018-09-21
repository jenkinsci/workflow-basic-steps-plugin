/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.model.Run;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.export.DataWriter;
import org.kohsuke.stapler.export.Flavor;
import org.kohsuke.stapler.export.ModelBuilder;
import org.kohsuke.stapler.export.NamedPathPruner;
import org.kohsuke.stapler.export.TreePruner;

/**
 * Uses {@link DataWriter} to produce information about model objects.
 */
public final class APIStep extends Step {
    
    public enum Format {
        JSON,
        STRUCTURE;
        // TODO add XML, JSON pretty-print, etc.
        public String getDisplayName() {
            return name(); // TODO
        }
    }

    private final String tree;
    // TODO optional field for alternate job name (relativizable?) + Run.number
    // TODO optional field for a (context-relative) URL to an arbitrary model object, if there were any way for Stapler dispatching to return this without actually running web methods or serving an index view
    private Format format = DescriptorImpl.getDefaultFormat();

    @DataBoundConstructor public APIStep(String tree) {
        this.tree = tree;
    }

    public Format getFormat() {
        return format;
    }

    @DataBoundSetter public void setFormat(Format format) {
        this.format = format;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    private static final class Execution extends SynchronousStepExecution<Object> {

        private static final long serialVersionUID = 1;

        private transient final APIStep step;

        Execution(APIStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override protected Object run() throws Exception {
            StringWriter w = new StringWriter();
            Run build = getContext().get(Run.class);
            DataWriter writer = Flavor.JSON.createDataWriter(build, w);
            TreePruner pruner = new NamedPathPruner(step.tree);
            new ModelBuilder().get(Run.class).writeTo(build, pruner, writer);
            String json = w.toString();
            switch (step.format) {
            case JSON:
                return json;
            case STRUCTURE:
                return translate(JSONObject.fromObject(json));
            default:
                throw new IllegalStateException();
            }
        }
    }


    private static Object translate(Object o) {
        if (o instanceof JSONObject) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Object _entry : ((JSONObject) o).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry) _entry;
                r.put((String) entry.getKey(), translate(entry.getValue()));
            }
            return r;
        } else if (o instanceof JSONArray) {
            List<Object> r = new ArrayList<>();
            for (Object element : (JSONArray) o) {
                r.add(translate(element));
            }
            return r;
        } else if (o instanceof JSONNull) {
            return null;
        } else {
            return o;
        }
    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "api";
        }

        @Override public String getDisplayName() {
            return "Retrieve API metadata from a Jenkins model object";
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(Run.class);
        }

        public static Format getDefaultFormat() {
            return Format.STRUCTURE;
        }

    }

}
