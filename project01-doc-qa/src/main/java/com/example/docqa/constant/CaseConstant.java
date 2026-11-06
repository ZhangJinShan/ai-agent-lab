package com.example.docqa.constant;

import java.util.List;

/**
 * 测试案例常量
 */
public class CaseConstant {

    /** 评估案例 */
    public static final List<String> EVAL_CASE = List.of(
            "登录接口的限流是多少？",
            "密码连续错几次会锁定?锁定多久?",
            "修改密码有频率限制吗?",
            "read-timeout 的默认值是多少?",
            "Redis 连接池 max-active 的默认值是多少?",
            "服务支持灰度发布吗?",
            "max-active 最大能配到多少?",
            "明天的天气怎么样?",
            "如何把超时时间改成 1 小时?",
            "登录接口支持 1000 次/分钟的限流吗?",
            "短信验证码收不到时,可以引导用户用什么替代方案?",
            "登录接口的限流能不能调大?",
            "数据库连接池 maximum-pool-size 能配到多少?",
            "服务支持暗黑模式或夜间模式吗?",
            "缓存 user-info 的 ttl 默认是 30 分钟吗?",
            "把 read-timeout 调到 10 分钟可以吗?",
            "密码连续错 3 次会锁定账号吗?",
            "Redis 连接池 max-active 默认是 100 吗?"
    );


    /** 记忆案例  */
    public static final List<String> MEMORY_CASE = List.of(
            "我叫老张,记住这个名字",
            "我叫什么?",
            "登录接口的限流是多少?",
            "那密码错误呢?"
    );

}
