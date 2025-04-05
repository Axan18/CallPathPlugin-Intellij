package axan18.methodcallpathdetectorplugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginTest extends LightJavaCodeInsightFixtureTestCase5 {

    private final MethodCallPathDetector detector = new MethodCallPathDetector();
    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
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
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
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
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
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
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
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
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(1, paths.size());
        assertTrue(List.of("foo", "bar", "baz").containsAll(paths.get(0)));
    }
    @Test
    void testMultiplePaths1(){
        String classCode = """
        class XYZ{
            void foo() { bar(); baz(); }
            void bar() { baz(); }
            void baz() { interestingMethod(); }
            void interestingMethod(){ return; }
        }""";
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(2, paths.size());
        assertTrue(paths.stream().anyMatch(path -> path.containsAll(List.of("foo", "bar", "baz"))));
        assertTrue(paths.stream().anyMatch(path -> path.containsAll(List.of("foo", "baz"))));

    }
    @Test
    void testMultiplePaths2(){
        String classCode = """
        class XYZ{
            void foo() { bar(); baz(); }
            void bar() { baz(); }
            void baz() { interestingMethod(); }
            void abc() { interestingMethod(); }
            void interestingMethod(){ return; }
        }""";
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(2, paths.size());
        assertTrue(paths.stream().anyMatch(path -> path.containsAll(List.of("foo", "bar", "baz"))));
        assertTrue(paths.stream().anyMatch(path -> path.containsAll(List.of("foo", "baz"))));

    }
    @Test
    void testMultipleClasses(){
        String classCodeA = """
        public class A{
            void foo() {
                B b = new B();
                b.bar();
            }
        }""";
        String classCodeB = """
        public class B{
            void bar() { baz(); }
            void baz() { interestingMethod(); }
            void interestingMethod(){ return; }
        }""";
        getFixture().configureByText("A.java", classCodeA);
        getFixture().configureByText("B.java", classCodeB);
        Project project = getFixture().getProject();
        PsiMethod start = findMethodByName(project, "foo");
        PsiMethod target = findMethodByName(project, "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(1, paths.size());
        List<String> expectedPath = List.of("foo", "bar", "baz");
        assertTrue(paths.get(0).containsAll(expectedPath) && expectedPath.containsAll(paths.get(0)));
    }
    @Test
    void testPolymorphicCalls(){ // not a problem if plugin doesn't check for polymorphic calls
        String classCodeA = """
                class Animal {
                    public void makeSound() {
                        System.out.println("Some generic sound");
                    }
                }""";
        String classCodeB = """
                class Dog extends Animal {
                    @Override
                    public void makeSound() {
                        System.out.println("Bark");
                    }
                }""";
        String classCodeC = """
                class Cat extends Animal {
                    @Override
                    public void makeSound() {
                        System.out.println("Meow");
                    }
                }""";
        String XYZ = """
                class XYZ {
                    void foo() {
                        Animal a = new Dog();
                        a.makeSound();
                    }
                    void bar() {
                        Animal a = new Cat();
                        a.makeSound();
                    }
                }""";
        PsiFile fileA = getFixture().configureByText("A.java", classCodeA);
        PsiFile fileB = getFixture().configureByText("B.java", classCodeB);
        PsiFile fileC = getFixture().configureByText("C.java", classCodeC);
        PsiFile fileXYZ = getFixture().configureByText("XYZ.java", XYZ);
        Project project = getFixture().getProject();
        PsiMethod start = findMethodByName(project, "foo");
        PsiMethod target = findMethodByName(project, "makeSound");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(1, paths.size());
        List<String> expectedPath = List.of("foo");
        assertTrue(paths.get(0).containsAll(expectedPath) && expectedPath.containsAll(paths.get(0)));
    }
    @Test
    void testThreadCall1(){
        String classCode = """
        class XYZ{
            void foo() {
                new Thread(() -> {
                    interestingMethod();
                }).start();
            }
            void interestingMethod(){ return; }
        }""";
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(0, paths.size());
    }
    @Test
    void testThreadCall2(){ // not a problem as we don't go to other thread
        String classCode = """
        class XYZ{
            void foo() {
                new Thread(() -> {
                    bar();
                }).start();
            }
            void bar() {
                interestingMethod();
            }
            void interestingMethod(){ return; }
        }""";
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "bar");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(1, paths.size());
        List<String> expectedPath = List.of("bar");
        assertTrue(paths.get(0).containsAll(expectedPath) && expectedPath.containsAll(paths.get(0)));
    }

    @Test
    void testLambdas(){
        String classCode = """
        class XYZ{
            void foo() {
                Runnable r = () -> {
                    interestingMethod();
                };
                r.run();
            }
            void interestingMethod(){ return; }
        }""";
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(1, paths.size());
        List<String> expectedPath = List.of("foo");
        assertTrue(paths.get(0).containsAll(expectedPath) && expectedPath.containsAll(paths.get(0)));
    }
    @Test
    void testInnerClass(){
        String classCode = """
        class XYZ {
            class Inner {
                void innerMethod() {
                    interestingMethod();
                }
            }
            void outerMethod() {
                Inner inner = new Inner();
                inner.innerMethod();
            }
            void interestingMethod(){ return; }
        }""";
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "outerMethod");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(1, paths.size());
        List<String> expectedPath = List.of("outerMethod", "innerMethod");
        assertTrue(paths.get(0).containsAll(expectedPath) && expectedPath.containsAll(paths.get(0)));
    }
    @Test
    void testExecutorService(){
        String classCode = """
        import java.util.concurrent.ExecutorService;
        import java.util.concurrent.Executors;

        class XYZ {
            void foo() {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    interestingMethod();
                });
                executor.shutdown();
            }
            void interestingMethod(){ return; }
        }""";
        PsiFile file = getFixture().configureByText("XYZ.java", classCode);
        PsiMethod start = findMethodByName(file.getProject(), "foo");
        PsiMethod target = findMethodByName(file.getProject(), "interestingMethod");
        detector.setStart(start);
        List<List<String>> paths = getPath(target);
        assertEquals(0, paths.size());
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