package axan18.methodcallpathdetectorplugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.junit.jupiter.api.Test;
import com.intellij.openapi.project.Project;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class PluginTest extends LightJavaCodeInsightFixtureTestCase5 {

    private MethodCallPathDetector detector = new MethodCallPathDetector();
    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    @Test
    void testMethodExtraction() {
        PsiFile file = getFixture().addFileToProject("XYZ.java", """
        class XYZ{
            void foo() { bar(); }
            void bar() { baz(); }
            void baz() { interestingMethod(); }
            void interestingMethod(){ return; }
        }""");

        PsiClass psiClass = readActionWrapper(() -> PsiTreeUtil.getChildOfType(file, PsiClass.class));

        PsiMethod[] methods = readActionWrapper(() -> psiClass != null ? psiClass.getMethods() : new PsiMethod[0]);

        assertNotNull(methods);
        assertEquals(4, methods.length);

        // Verify method names
        Set<String> expectedNames = Set.of("foo", "bar", "baz", "interestingMethod");
        Set<String> actualNames = readActionWrapper(() -> Arrays.stream(methods)
                .map(PsiMethod::getName)
                .collect(Collectors.toSet()));

        assertTrue(expectedNames.containsAll(actualNames));
    }

    @Test
    void testBasic(){
        String classCode = """
        class XYZ{
            void foo() { bar(); }
            void bar() { baz(); }
            void baz() { interestingMethod(); }
            void interestingMethod(){ return; }
        }""";
        PsiFile file = getFixture().addFileToProject("XYZ.java", classCode);
        Project project = getFixture().getProject();
        assertNotNull(file);
        assertTrue(file.isValid());
        Optional<PsiMethod> startMethod = findMethodByName(project, "foo");
        Optional<PsiMethod> targetMethod = findMethodByName(project, "interestingMethod");
        assertTrue(startMethod.isPresent());
        assertTrue(targetMethod.isPresent());
        PsiMethod start = startMethod.get();
        PsiMethod target = targetMethod.get();
        detector.setStart(start);
        List<String> path = ProgressManager.getInstance().runProcess(
                () -> ReadAction.compute(() ->
                        detector.findCallPath(target, new ArrayList<>(), new HashSet<>())),
                new EmptyProgressIndicator());
        assertTrue(List.of("foo", "bar", "baz").containsAll(path));

    }

    private static <T> T readActionWrapper(Computable<T> computable) {
        return ApplicationManager.getApplication().runReadAction(computable);
    }
    private static Optional<PsiMethod> findMethodByName(Project project, String name) {
        return readActionWrapper(() -> {
            PsiMethod[] methods = PsiShortNamesCache.getInstance(project)
                    .getMethodsByName(name, GlobalSearchScope.allScope(project));

            return methods.length > 0 ? Optional.of(methods[0]) : Optional.empty();
        });
    }
}
