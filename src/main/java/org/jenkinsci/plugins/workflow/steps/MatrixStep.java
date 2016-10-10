package org.jenkinsci.plugins.workflow.steps;

import com.google.common.collect.Lists;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.GroovyStep;
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.GroovyStepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatrixStep extends GroovyStep {
    private Map<String,List<Object>> axes;

    @DataBoundConstructor
    public MatrixStep(@Nonnull Map<String,List<Object>> axes) {
        this.axes = axes;
    }

    public Map<String,List<Object>> getAxes() {
        return axes;
    }

    public List<Map<String,Object>> getPermutations() {
        return getPermutations(Lists.newArrayList(axes.keySet()));
    }

    public List<Map<String,Object>> getPermutations(List<String> keys) {
        List<Map<String,Object>> permutations = new ArrayList<>();

        if (keys.isEmpty()) {
            return permutations;
        }

        String thisKey = keys.remove(0);
        if (axes.containsKey(thisKey)) {
            List<Object> thisVals = axes.get(thisKey);

            if (keys.isEmpty()) {
                for (Object o : thisVals) {
                    Map<String,Object> valList = new HashMap<>();
                    valList.put(thisKey, o);
                    permutations.add(valList);
                }
            } else {
                List<Map<String,Object>> recursePermutations = getPermutations(keys);

                for (Map<String,Object> thisPerm : recursePermutations) {
                    for (Object o : thisVals) {
                        Map<String,Object> valList = new HashMap<>();
                        valList.put(thisKey, o);
                        valList.putAll(thisPerm);
                        permutations.add(valList);
                    }
                }
            }
        } else {
            return getPermutations(keys);
        }

        return permutations;
    }

    @Extension
    public static class DescriptorImpl extends GroovyStepDescriptor {
        @Override
        public String getFunctionName() {
            return "matrix";
        }

        @Override
        public String getDisplayName() {
            return "Matrix step";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}
