import com.cloudbees.groovy.cps.NonCPS
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.GroovyStepExecution
import org.jenkinsci.plugins.workflow.steps.MatrixStep

public class MatrixStepExecution extends GroovyStepExecution {
    public void call(Closure body) {
        List<Map<String,Object>> permutations = ((MatrixStep)getStep()).permutations

        Map<String,Closure> parallelBlocks = new HashMap<>()

        for (int i = 0; i < permutations.size(); i++) {
            Map<String,Object> thisPermutation = permutations.get(i)

            String blockName = getBlockName(thisPermutation.keySet())
            List<String> envList = getEnvList(thisPermutation)

            parallelBlocks.put(blockName, { ->
                withEnv(envList) {
                    body()
                }
            })
        }

        parallel(parallelBlocks)
    }

    @NonCPS
    String getBlockName(Set<String> keys) {
        return keys.join("+")
    }

    @NonCPS
    List<String> getEnvList(Map<String,Object> permutation) {
        return permutation.collect { k, v ->
            "${k}=${v}"
        }
    }
}