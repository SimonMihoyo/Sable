package me.kirara.sable.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限码字典与开发种子数据的一致性测试。
 *
 * <p><b>它防的是什么：</b>{@link Permission} 与 {@code db/dev/R__dev_seed.sql} 是同一套权限码的
 * 两份副本，谁也不会因为对方改了而报错。漂移的表现极难定位 —— 种子给角色配了
 * {@code audit_log:exportt}（多打一个 t）而字典里是 {@code export}，于是那条权限从未生效、
 * 导出接口对这个角色永远 403，而 {@code role.permissions} 看起来一切正常。
 * 这类"名称拼写"错误没有任何编译器能发现。</p>
 *
 * <p><b>为什么这个测试在 {@code sable-app}：</b>种子数据是启动模块的资源
 * （{@code db/dev/} 只随它打包），而 {@link Permission} 在 {@code sable-common} ——
 * 只有启动模块的测试 classpath 同时看得见两者。包名沿用 {@code me.kirara.sable.common}，
 * 便于按被测类定位。</p>
 *
 * <p><b>限制：</b>只校验"种子里用到的码都在字典里"（漏配方向）。反方向
 * （字典里有、但没有任何角色使用）故意不校验 —— 那是完全正常的，
 * 新权限码本来就该先定义好、等对应模块落地时再授予。</p>
 */
class PermissionDictionaryTest {

    /** 种子数据在 classpath 上的位置（随 {@code src/main/resources} 一起打包）。 */
    private static final String SEED_RESOURCE = "/db/dev/R__dev_seed.sql";

    /** 权限码的形态：两段小写加下划线，中间一个冒号。 */
    private static final Pattern PERMISSION_CODE = Pattern.compile("\"([a-z_]+:[a-z_]+)\"");

    /** FR-6.2.1 要求的五个预置角色。 */
    private static final List<String> PRESET_ROLES = List.of(
            "SUPER_ADMIN", "COMPLIANCE_OFFICER", "AUDITOR", "DATA_STEWARD", "VIEWER");

    @Test
    @DisplayName("字典里没有重复或形态错误的权限码")
    void dictionaryIsWellFormed() {
        assertEquals(Permission.ALL.size(), new LinkedHashSet<>(Permission.ALL).size(),
                "权限码有重复 —— 重复项会让 ALL 悄悄变短，也会让按它做的校验漏掉一个码");
        for (String code : Permission.ALL) {
            assertNotNull(code);
            assertTrue(PERMISSION_CODE.matcher("\"" + code + "\"").matches(),
                    "权限码应形如 xx_yy:zz（两段小写、一个冒号），实际为：" + code);
        }
    }

    @Test
    @DisplayName("种子数据里用到的权限码都在字典中定义")
    void seedPermissionsAreDeclaredInDictionary() throws IOException {
        Set<String> used = extractPermissionCodes(readSeed());

        // 先确认真的解析出了东西。否则正则或路径一旦失效，下面的断言会因为"空集没有越界项"
        // 而恒真 —— 一个永远绿灯的守卫比没有守卫更糟。
        assertFalse(used.isEmpty(),
                "没从种子数据里解析出任何权限码，文件路径或权限码形态可能已经变了");

        Set<String> unknown = new LinkedHashSet<>(used);
        unknown.removeAll(Permission.ALL);
        assertEquals(Set.of(), unknown,
                "种子数据里出现了字典中没有的权限码。这个码不会有任何接口认它，多半是拼写错误："
                        + "要么改种子，要么在 Permission 里补常量");
    }

    @Test
    @DisplayName("FR-6.2.1 要求的五个预置角色都在种子数据里")
    void presetRolesAreSeeded() throws IOException {
        String seed = readSeed();

        for (String role : PRESET_ROLES) {
            assertTrue(seed.contains("'" + role + "'"),
                    "种子数据缺少预置角色 " + role + "（FR-6.2.1 要求五个预置角色）");
        }
    }

    // ------------------------------------------------------------------ 辅助

    private static String readSeed() throws IOException {
        try (InputStream in = PermissionDictionaryTest.class.getResourceAsStream(SEED_RESOURCE)) {
            assertNotNull(in, SEED_RESOURCE + " 不在测试 classpath 上");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> extractPermissionCodes(String seed) {
        Matcher matcher = PERMISSION_CODE.matcher(seed);
        Set<String> found = new LinkedHashSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
