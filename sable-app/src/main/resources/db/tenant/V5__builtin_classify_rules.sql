-- ============================================================
-- 内置敏感数据识别规则（40 条）
-- ============================================================
-- 内置规则是**产品初始数据**，不是演示数据：每个租户的 schema 建好时都要有，
-- 因此放在 db/tenant/（每个租户执行一次），而不是 db/dev/。
--
-- 约定：
--   · match_type = KEYWORD   → pattern 是 JSON 数组，任一关键词命中字段名/注释/采样值即可
--   · match_type = REGEX     → pattern 是正则，对字段名/注释/采样值做子串匹配
--   · match_type = COMPOSITE → pattern 是 {"regex":..., "keywords":[...], "min_match":n}
--                             正则命中记 1 分、关键词命中记 1 分，达到 min_match 才算命中
--   · category 用中文分类名（与 db_column.category 的展示口径一致）
--   · sensitivity 只能是 SPI / PI / IMPORTANT / GENERAL（见列上的 CHECK 约束）
--   · priority 越大越优先；同一字段命中多条规则时，引擎按优先级从高到低返回
--
-- 为什么"手机号 / 身份证 / 邮箱"这类用 COMPOSITE(min_match=1) 而不是纯 REGEX：
-- 当前扫描只读结构（字段名 + 注释），**没有采样数据**。纯正则只能匹配到"值"，
-- 对 id_card、mobile 这类字段名无能为力；反过来纯关键词又识别不了脏字段名。
-- min_match=1 表示"名字像 **或** 值像"，两个信号都能命中；银行卡号这种误报代价高的
-- 规则才用 min_match=2（名字像 **且** 值像）。
--
-- 内置规则允许停用（status = DISABLED），但不允许删除（接口层已拦截），
-- 否则"这个租户为什么识别不出来"会变成一件需要翻历史数据才能回答的事。
INSERT INTO classify_rule (name, description, category, sensitivity, match_type, pattern, priority, is_builtin, status)
VALUES
    ('身份证号识别', '18 位居民身份证号；字段名或内容命中均可', '身份信息', 'SPI', 'COMPOSITE',
     '{"keywords":["身份证","id_card","idcard","id_no","identity_no","cert_no","证件号"],"regex":"^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$","min_match":1}', 100, TRUE, 'ACTIVE'),
    ('手机号识别', '中国大陆 11 位手机号；字段名或内容命中均可', '个人基本信息', 'PI', 'COMPOSITE',
     '{"keywords":["手机号","手机","mobile","phone","telephone","msisdn"],"regex":"(?<!\\d)1[3-9]\\d{9}(?!\\d)","min_match":1}', 95, TRUE, 'ACTIVE'),
    ('银行卡号识别', '16~19 位银行卡号；要求字段名与内容同时命中，压低误报', '财务信息', 'SPI', 'COMPOSITE',
     '{"keywords":["银行卡","bank_card","card_no","卡号"],"regex":"(?<!\\d)\\d{16,19}(?!\\d)","min_match":2}', 95, TRUE, 'ACTIVE'),
    ('电子邮箱识别', '邮箱地址；字段名或内容命中均可', '个人基本信息', 'PI', 'COMPOSITE',
     '{"keywords":["邮箱","电子邮件","email","e_mail","mail"],"regex":"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","min_match":1}', 90, TRUE, 'ACTIVE'),
    ('密码识别', '登录口令 / 密码字段', '账号凭证', 'SPI', 'KEYWORD',
     '["密码","password","passwd","pwd","pass_word"]', 90, TRUE, 'ACTIVE'),
    ('密钥令牌识别', 'API 密钥、访问令牌等凭据', '账号凭证', 'SPI', 'KEYWORD',
     '["密钥","令牌","token","secret_key","access_key","api_secret","private_key"]', 88, TRUE, 'ACTIVE'),
    ('护照号识别', '护照号码；字段名或内容命中均可', '身份信息', 'SPI', 'COMPOSITE',
     '{"keywords":["护照","passport"],"regex":"^[EeGgDdSsPpHh]\\d{8}$","min_match":1}', 85, TRUE, 'ACTIVE'),
    ('信用卡安全码识别', 'CVV / CVC 等卡验证码', '财务信息', 'SPI', 'KEYWORD',
     '["cvv","cvc","card_verify","安全码"]', 85, TRUE, 'ACTIVE'),
    ('指纹识别', '指纹特征数据', '生物特征', 'SPI', 'KEYWORD',
     '["指纹","fingerprint","finger_print"]', 85, TRUE, 'ACTIVE'),
    ('人脸特征识别', '人脸图像与面部特征', '生物特征', 'SPI', 'KEYWORD',
     '["人脸","面部特征","face_id","face_feature","face_image"]', 85, TRUE, 'ACTIVE'),
    ('病历信息识别', '病历、病史、诊断结论', '健康医疗', 'SPI', 'KEYWORD',
     '["病历","病史","诊断","medical_record","medical_history","diagnosis"]', 85, TRUE, 'ACTIVE'),
    ('虹膜识别', '虹膜特征数据', '生物特征', 'SPI', 'KEYWORD',
     '["虹膜","iris"]', 82, TRUE, 'ACTIVE'),
    ('声纹识别', '声纹特征数据', '生物特征', 'SPI', 'KEYWORD',
     '["声纹","voiceprint","voice_print"]', 82, TRUE, 'ACTIVE'),
    ('基因信息识别', 'DNA / 基因数据', '生物特征', 'SPI', 'KEYWORD',
     '["基因","dna","genome","genetic"]', 82, TRUE, 'ACTIVE'),
    ('驾驶证号识别', '驾驶证 / 驾照编号', '身份信息', 'SPI', 'KEYWORD',
     '["驾驶证","驾照","driver_license","driving_license"]', 80, TRUE, 'ACTIVE'),
    ('社会保障号识别', '社保号 / 社会保障号', '身份信息', 'SPI', 'KEYWORD',
     '["社保号","社会保障号","social_security","ssn"]', 80, TRUE, 'ACTIVE'),
    ('处方信息识别', '处方与用药记录', '健康医疗', 'SPI', 'KEYWORD',
     '["处方","prescription","medication"]', 80, TRUE, 'ACTIVE'),
    ('港澳台通行证识别', '港澳通行证 / 台胞证 / 回乡证', '身份信息', 'SPI', 'KEYWORD',
     '["通行证","回乡证","台胞证","港澳通行证"]', 78, TRUE, 'ACTIVE'),
    ('过敏史识别', '过敏史与过敏源', '健康医疗', 'SPI', 'KEYWORD',
     '["过敏","allergy","allergies"]', 78, TRUE, 'ACTIVE'),
    ('军官证识别', '军官证 / 士兵证', '身份信息', 'SPI', 'KEYWORD',
     '["军官证","士兵证","military_id"]', 75, TRUE, 'ACTIVE'),
    ('薪资信息识别', '工资、薪资、薪酬', '财务信息', 'SPI', 'KEYWORD',
     '["工资","薪资","薪酬","salary","wage","payroll","compensation"]', 75, TRUE, 'ACTIVE'),
    ('支付账号识别', '支付宝 / 微信支付等支付账号', '财务信息', 'SPI', 'KEYWORD',
     '["支付宝","微信支付","alipay","wechat_pay","pay_account","payment_account"]', 70, TRUE, 'ACTIVE'),
    ('体检报告识别', '体检结果与健康档案', '健康医疗', 'SPI', 'KEYWORD',
     '["体检","physical_exam","health_check","checkup"]', 70, TRUE, 'ACTIVE'),
    ('IP地址识别', 'IPv4 地址；字段名或内容命中均可', '位置信息', 'PI', 'COMPOSITE',
     '{"keywords":["ip_address","ip_addr","client_ip","remote_ip","login_ip"],"regex":"(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])","min_match":1}', 70, TRUE, 'ACTIVE'),
    ('家庭住址识别', '家庭 / 居住地址', '个人基本信息', 'PI', 'KEYWORD',
     '["住址","家庭地址","居住地址","地址","home_address","home_addr","street_address","mailing_address","residence"]', 65, TRUE, 'ACTIVE'),
    ('车牌号识别', '中国大陆机动车号牌；字段名或内容命中均可', '其他标识', 'PI', 'COMPOSITE',
     '{"keywords":["车牌","plate_no","license_plate","car_no"],"regex":"^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][A-Z0-9]{4,5}[A-Z0-9挂学警港澳]$","min_match":1}', 65, TRUE, 'ACTIVE'),
    ('姓名识别', '个人姓名字段', '个人基本信息', 'PI', 'KEYWORD',
     '["姓名","真实姓名","full_name","real_name"]', 60, TRUE, 'ACTIVE'),
    -- 单独的 address / addr 含义模糊（email_addr 也算），放到低优先级：
    -- 邮箱规则命中时优先级更高，自动分级取最高分即可；它的作用是别把"纯地址"字段漏掉
    ('通用地址识别', 'address / addr 字段（含义模糊，优先级较低）', '个人基本信息', 'PI', 'KEYWORD',
     '["address","addr"]', 45, TRUE, 'ACTIVE'),
    ('经纬度识别', 'GPS / 经纬度坐标', '位置信息', 'PI', 'KEYWORD',
     '["经度","纬度","longitude","latitude","gps","geo_point"]', 60, TRUE, 'ACTIVE'),
    ('客户名单识别', '客户 / 客户列表（企业机密）', '企业机密', 'IMPORTANT', 'KEYWORD',
     '["客户名单","客户列表","customer_list","client_list"]', 60, TRUE, 'ACTIVE'),
    ('统一社会信用代码识别', '18 位统一社会信用代码；字段名或内容命中均可', '其他标识', 'IMPORTANT', 'COMPOSITE',
     '{"keywords":["统一社会信用代码","credit_code","uscc"],"regex":"^(?=.*[A-HJ-NPQRTUWXY])[0-9A-HJ-NPQRTUWXY]{18}$","min_match":1}', 60, TRUE, 'ACTIVE'),
    ('出生日期识别', '出生日期 / 生日', '个人基本信息', 'PI', 'KEYWORD',
     '["出生日期","出生年月","生日","birthday","birth_date","date_of_birth"]', 55, TRUE, 'ACTIVE'),
    ('交易流水识别', '交易流水号与交易记录', '财务信息', 'PI', 'KEYWORD',
     '["交易流水","流水号","transaction","trade_no"]', 55, TRUE, 'ACTIVE'),
    ('开户行识别', '银行卡开户行信息', '财务信息', 'PI', 'KEYWORD',
     '["开户行","开户银行","bank_name","bank_branch"]', 50, TRUE, 'ACTIVE'),
    ('合同信息识别', '合同与协议内容（企业机密）', '企业机密', 'IMPORTANT', 'KEYWORD',
     '["合同","contract","agreement"]', 50, TRUE, 'ACTIVE'),
    ('性别识别', '性别字段', '个人基本信息', 'GENERAL', 'KEYWORD',
     '["性别","gender","sex"]', 40, TRUE, 'ACTIVE'),
    ('学号识别', '学号 / 学生编号', '其他标识', 'GENERAL', 'KEYWORD',
     '["学号","student_no","student_id"]', 40, TRUE, 'ACTIVE'),
    ('民族识别', '民族信息', '个人基本信息', 'GENERAL', 'KEYWORD',
     '["民族","ethnicity","ethnic"]', 35, TRUE, 'ACTIVE'),
    ('员工工号识别', '员工工号 / 职员工号', '其他标识', 'GENERAL', 'KEYWORD',
     '["工号","employee_no","staff_no","job_number"]', 35, TRUE, 'ACTIVE'),
    ('邮政编码识别', '邮政编码', '其他标识', 'GENERAL', 'KEYWORD',
     '["邮编","邮政编码","postcode","postal_code","zip_code"]', 30, TRUE, 'ACTIVE');
