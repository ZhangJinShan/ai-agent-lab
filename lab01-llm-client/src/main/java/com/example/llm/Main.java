package com.example.llm;

import java.io.InputStream;
import java.util.Properties;

/**
 * @author zhangjinshan
 * @description: Main Test
 * @create: 2026/10/11
 **/
public class Main {

    public static void main(String[] args) {
        // 1. 对 classpath 下的 application.properties 文件
        Properties properties = new Properties();
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream("application.properties")){
            if (in == null) {
                System.err.println("找不到 application.properties,检查文件是否在 src/main/resources 下");
                return;
            }
            properties.load(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 2. 读环境变量里的 api-key
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("请先设置环境变量 LLM_API_KEY(设置后需重启 IDE/终端)");
            return;
        }

        // 3. 构造 client —— 参数不对会抛 IllegalArgumentException
        LlmClient llmClient = new LlmClient(properties.getProperty("llm.baseUrl"), properties.getProperty("llm.model"), apiKey);

        // 4. 打印验证(baseUrl 打印出来没问题,apiKey 千万不要打印!)
//        System.out.println("配置加载成功!");
//        System.out.println("  baseUrl = " + properties.getProperty("llm.baseUrl"));
//        System.out.println("  model   = " + properties.getProperty("llm.model"));
//        System.out.println("  apiKey  = 已加载(" + apiKey.length() + " 个字符)");

//        String systemPrompt = "你是一个简洁的助手";
//
//        String userMessage = "你好,请用一句话介绍你自己";
//
//
//        String answer = llmClient.chat("你是一个简洁的助手", "你好,请用一句话介绍你自己");
//
//        System.out.println("模型回复: " + answer);
//
//        System.out.println("***************************************");
//
//        llmClient.chatStream(systemPrompt, userMessage, System.out::println);
//
//        System.out.println("***************************************");

        String extraSystemPrompt = "你是一个信息提取器，从用户提供的文本中提取信息，只输出一个 JSON 对象:" +
                "格式: {\"name\": \"姓名\", \"years\": 工作年限整数, \"skills\": [\"技能1\", \"技能2\"]}" +
                "规则:\n" +
                "1. 不要输出 JSON 以外的任何文字,包括解释和 markdown 代码块标记\n";

//        2. 文本中找不到的字段用空字符串/0/空数组,不要编造

//        String userText = "本人叫赵景珩，从 2017 年 7 月正式踏入软件开发相关行业，中间有半年时间脱产进修深度学习相关课程，实际在岗工作算下来有 8 年工作经历；" +
//                "日常工作中主要负责后端接口开发、MySQL 数据库调优，也会写单元测试脚本，偶尔参与产品需求评审，能够熟练使用 Java、SpringBoot 框架，掌握 Redis 缓存技术，" +
//                "同时具备基础的 Linux 服务器运维能力，平时也会对接前端人员做联调工作，" +
//                "之前在多家中型互联网企业任职，参与过电商交易系统、会员管理平台的迭代建设，业余时间还自学过简单的数据可视化处理。";

        String userText = "本人叫赵景珩，于 2017 年 7 月正式踏入软件开发相关行业，深耕后端开发领域多年。职业发展期间，曾有半年时间脱产进修深度学习相关课程，系统学习人工智能基础理论与实践技术，拓宽自身技术认知边界，剔除脱产进修周期，实际在岗工作经历共计 8 年。\n" +
                "\n" +
                "日常工作中，我主要负责后端接口的设计与开发工作，结合业务场景输出高可用、易维护的服务接口；擅长 MySQL 数据库调优，针对慢 SQL、索引优化、事务机制、表结构改造等问题开展排查优化，保障数据库的查询性能与业务稳定性。开发过程中重视代码质量，会编写完善的单元测试脚本，对核心业务逻辑进行校验，提前规避潜在 bug，降低线上故障风险。也会参与产品需求评审环节，站在技术角度评估需求可行性、实现成本以及潜在风险，主动和产品沟通提出合理化落地建议。\n" +
                "\n" +
                "技术栈方面，能够熟练使用 Java 编程语言以及 SpringBoot 框架进行业务开发，熟练运用 Redis 缓存技术，合理设计缓存策略，缓解数据库访问压力，提升系统响应速度；同时具备基础的 Linux 服务器运维能力，可完成基础环境部署、日志排查、服务启停等操作。项目推进阶段，经常对接前端同事开展接口联调，及时定位并解决前后端交互过程中出现的数据格式、参数传参等各类问题，保障项目迭代进度。\n" +
                "\n" +
                "过往曾在多家中型互联网企业任职，深度参与过电商交易系统、会员管理平台的迭代建设，熟悉交易链路、会员权益、用户权限等相关业务逻辑，经历过业务版本迭代、功能升级、线上问题复盘全流程。在工作之余，我也保持持续学习的习惯，利用业余时间自学简单的数据可视化处理相关知识，尝试将数据处理能力融入业务场景，不断丰富自身综合技术能力，能够较好的适配后端开发岗位各类业务开发工作。";

        LlmClient.Resume resume = llmClient.extractJson(extraSystemPrompt, userText);
        System.out.println(resume);


        LlmClient.Resume _resume = llmClient.chatExtractJson(extraSystemPrompt.replace("JSON", ""), userText);
        System.out.println(_resume);
    }
}
