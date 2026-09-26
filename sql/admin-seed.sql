-- P6 管理员种子账号：13800000003，登录走短信验证码流程（POST /auth/code 发码）
-- 已存在则仅升角色，不存在则新造一条（status=1 启用）。
UPDATE tb_user SET role = 3 WHERE phone = '13800000003';

INSERT INTO tb_user (phone, nick_name, `role`, `status`)
SELECT '13800000003', '平台管理员', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE phone = '13800000003');
