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
import org.jetbrains.annotations.NotNull;
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
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(1, paths.size());
        assertTrue(List.of("foo", "bar", "baz").containsAll(paths.get(0)));
    }
    @Test
    void testNoPath(){
        String classCode = """
        class XYZ{
            void foo() { bar(); }
            void bar() { baz(); }
            void baz() { foo(); }
            void interestingMethod(){ return; }
        }""";
        PsiFile file = getFixture().addFileToProject("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(0, paths.size());
    }
    @Test
    void testLoopCalls(){
        String classCode = """
        class XYZ{
            void foo() { bar(); }
            void bar() { baz(); }
            void baz() { interestingMethod(); }
            void interestingMethod(){ foo(); }
        }""";
        PsiFile file = getFixture().addFileToProject("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(1, paths.size());
        assertTrue(List.of("foo", "bar", "baz").containsAll(paths.get(0)));
    }
    @Test
    void testRecursiveTarget(){
        String classCode = """
        class XYZ{
            void foo() { bar(); }
            void bar() { baz(); }
            void baz() { interestingMethod(); }
            void interestingMethod(){ interestingMethod(); }
        }""";
        PsiFile file = getFixture().addFileToProject("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(1, paths.size());
        assertTrue(List.of("foo", "bar", "baz").containsAll(paths.get(0)));
    }
    @Test
    void testMultiplePaths(){
        String classCode = """
        class XYZ{
            void foo() { bar(); baz(); }
            void bar() { baz(); }
            void baz() { interestingMethod(); }
            void interestingMethod(){ return; }
        }""";
        PsiFile file = getFixture().addFileToProject("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(2, paths.size());
        assertTrue(List.of("foo", "bar", "baz").containsAll(paths.get(0)));
        assertTrue(List.of("foo", "baz").containsAll(paths.get(1)));
    }

    private List<List<String>> getPath(PsiMethod target) {
        return ProgressManager.getInstance().runProcess(
                () -> ReadAction.compute(() ->
                        detector.findCallPaths(target, new ArrayList<>(), new HashSet<>())),
                new EmptyProgressIndicator());
    }

    private static <T> T readActionWrapper(Computable<T> computable) {
        return ApplicationManager.getApplication().runReadAction(computable);
    }

    private static @NotNull PsiMethod findMethodByName(Project project, String name) {
        Optional<PsiMethod> method =  readActionWrapper(() -> {
                PsiMethod[] methods = PsiShortNamesCache.getInstance(project)
                        .getMethodsByName(name, GlobalSearchScope.allScope(project));

                return methods.length > 0 ? Optional.of(methods[0]) : Optional.empty();
            });
        assertTrue(method.isPresent());
        return method.get();
    }
}
