-- Rotaciona a senha do seed ROOT_MASTER inserida em V1__init.sql.
--
-- V1 documentava em comentário a senha em texto puro do hash BCrypt inserido
-- ('Admin@2026!'). Migrations Flyway rodam identicamente em todo ambiente,
-- então essa credencial conhecida chegaria a produção caso alguém esqueça de
-- trocá-la manualmente após o primeiro deploy. Não é seguro depender de um
-- passo manual não automatizado para uma conta com controle total do sistema.
--
-- Esta migration substitui o hash por um valor ALEATÓRIO e DESCONHECIDO
-- (bcrypt de um UUID gerado em tempo de execução, nunca exposto). Não há mais
-- senha padrão para root@cvfacil.ng.
--
-- Nunca editar V1__init.sql em retrospecto — migrations já aplicadas em
-- qualquer ambiente têm checksum validado pelo Flyway; a correção precisa
-- vir em uma migration nova.
--
-- Para definir a senha real de acesso após o deploy, rode manualmente:
--   UPDATE users SET password_hash = crypt('<senha-forte-escolhida>', gen_salt('bf', 12))
--   WHERE email = 'root@cvfacil.ng';

CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE users
SET password_hash = crypt(gen_random_uuid()::text, gen_salt('bf', 12))
WHERE email = 'root@cvfacil.ng' AND role = 'ROOT_MASTER';
