package me.kirara.sable.system.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接口权限绑定的守卫测试。
 *
 * <p><b>它防的是什么：</b>新增一个接口却忘了挂 {@code @PreAuthorize}。这种漏配不会让编译失败、
 * 也不会让任何既有用例变红 —— 它的表现是"接口对**任何已登录用户**开放"，
 * 而且从代码上完全看不出来（没写注解的接口和写对了的接口长得一样）。
 * 一个合规系统里，这类静默的越权面不该靠 code review 的记性来守。</p>
 *
 * <p><b>三条断言分别对应三个不同的漏法：</b></p>
 * <ol>
 *   <li>整个控制器没挂注解（扫描本模块所有 {@code @RestController}，只允许白名单里的例外）；</li>
 *   <li>挂了，但把权限码写成了裸字符串 —— 拼错不会编译失败，只会让接口对所有人 403；</li>
 *   <li>类级规则把"读自己"的接口也一起拦了（{@code /system/users/me} 的覆盖）。</li>
 * </ol>
 */
class ControllerAuthorizationTest {

    /** 本模块的控制器包 —— 新增模块时把对应包加进来。 */
    private static final String CONTROLLER_PACKAGE = "me.kirara.sable.system.controller";

    /**
     * 允许不挂 {@code @PreAuthorize} 的控制器及其理由。
     *
     * <p>{@link AuthController} 是认证入口：{@code /auth/login}、{@code /auth/refresh}、
     * {@code /auth/logout} 都在白名单里（它们本来就无法携带令牌），
     * 而 {@code PUT /auth/password} 需要的是"已认证"而非某个权限码 ——
     * 改自己的密码不该要求管理员权限，那由 {@code anyRequest().authenticated()} 兜住。</p>
     */
    private static final Set<Class<?>> EXEMPT_CONTROLLERS = Set.of(AuthController.class);

    @Test
    @DisplayName("本模块每个控制器都必须有 @PreAuthorize，例外只有白名单里那几个")
    void everyControllerDeclaresAuthorization() {
        Set<Class<?>> missing = new LinkedHashSet<>();
        for (Class<?> controller : scanControllers()) {
            if (EXEMPT_CONTROLLERS.contains(controller)) {
                continue;
            }
            if (!hasPreAuthorize(controller)) {
                missing.add(controller);
            }
        }

        assertEquals(Set.of(), missing,
                "以下控制器既没有 @PreAuthorize 也不在白名单里 —— 它的所有接口对任何登录用户开放。"
                        + "要么挂上注解，要么把它加进 EXEMPT_CONTROLLERS 并写明为什么可以公开："
                        + missing.stream().map(Class::getSimpleName).toList());
    }

    @Test
    @DisplayName("权限码必须引用 Permission 字典常量，不写裸字符串")
    void permissionCodesComeFromTheDictionary() {
        for (Class<?> controller : scanControllers()) {
            for (PreAuthorize annotation : findPreAuthorizeAnnotations(controller)) {
                // 只检查**真正判权限**的那些规则。"isAuthenticated()" 这类"已认证即可"
                // 本来就不涉及权限码，不该被这条断言波及。
                if (!annotation.value().contains("hasAuthority(")) {
                    continue;
                }
                assertTrue(annotation.value().contains("me.kirara.sable.common.Permission"),
                        controller.getSimpleName() + " 的权限码应引用字典常量（"
                                + "T(me.kirara.sable.common.Permission).XXX），而实际写的是："
                                + annotation.value());
            }
        }
    }

    /**
     * 类级 {@code system:manage} 会把"读自己"的接口一起拦掉。
     *
     * <p>漏掉这条覆盖的表现是：审计员登录后前端调 {@code /system/users/me} 得 403，
     * 取不到自己的角色与权限 → 菜单空白 → 用户被卡在登录页。
     * 一个纯粹的读接口把所有人挡在门外，且错误码看起来像"权限没配对"。</p>
     */
    @Test
    @DisplayName("/system/users/me 用 isAuthenticated() 覆盖类级规则")
    void currentUserEndpointOverridesClassRule() throws Exception {
        Method me = UserController.class.getMethod("me");

        PreAuthorize annotation = AnnotationUtils.findAnnotation(me, PreAuthorize.class);

        assertNotNull(annotation, "UserController.me 缺少方法级 @PreAuthorize —— "
                + "它会继承类级的 system:manage，非管理员连自己的信息都读不到");
        assertEquals("isAuthenticated()", annotation.value());
    }

    // ------------------------------------------------------------------ 辅助

    /** 扫描控制器包下所有 {@code @RestController}。 */
    private static Set<Class<?>> scanControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<Class<?>> controllers = new LinkedHashSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            controllers.add(loadClass(definition.getBeanClassName()));
        }
        assertTrue(controllers.size() >= 4,
                "只扫到 " + controllers.size() + " 个控制器，扫描包名或 classpath 可能已失效："
                        + controllers);
        return controllers;
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("扫描到的类无法加载: " + className, e);
        }
    }

    private static boolean hasPreAuthorize(Class<?> controller) {
        return !findPreAuthorizeAnnotations(controller).isEmpty();
    }

    /** 类级与方法级的 {@code @PreAuthorize} 一并收集。 */
    private static List<PreAuthorize> findPreAuthorizeAnnotations(Class<?> controller) {
        List<PreAuthorize> annotations = new java.util.ArrayList<>();
        PreAuthorize onClass = AnnotationUtils.findAnnotation(controller, PreAuthorize.class);
        if (onClass != null) {
            annotations.add(onClass);
        }
        for (Method method : controller.getDeclaredMethods()) {
            PreAuthorize onMethod = AnnotationUtils.findAnnotation(method, PreAuthorize.class);
            if (onMethod != null) {
                annotations.add(onMethod);
            }
        }
        return annotations;
    }
}
