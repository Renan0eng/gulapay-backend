-- =============================================================================
-- V13 — Extrai o endereço embutido em `cliente` para entidade 1:N.
--
-- Etapas (executadas atomicamente pelo Flyway dentro de uma transação):
--   1. Criar a tabela `endereco_cliente`.
--   2. Backfill: para cada cliente que tenha qualquer campo de endereço
--      preenchido, criar uma linha em `endereco_cliente` marcada como
--      principal=TRUE e rotulo='Principal'.
--   3. Dropar as 7 colunas embutidas de `cliente`.
--
-- Vinculado à Sprint 2 (Comanda nos 3 canais): comandas DELIVERY exigem
-- uma FK para `endereco_cliente`, e clientes passam a poder ter múltiplos
-- endereços (casa, trabalho, etc).
--
-- Backlog: BACKLOG.md seção 2 (V13) e seção 9 (refactor 1:N).
-- =============================================================================

-- 1. Tabela endereco_cliente
CREATE TABLE endereco_cliente (
    id              BIGSERIAL    PRIMARY KEY,
    cliente_id      BIGINT       NOT NULL REFERENCES cliente(id),
    rotulo          VARCHAR(40)  NOT NULL DEFAULT 'Principal',
    logradouro      VARCHAR(150) NOT NULL,
    numero          VARCHAR(20),
    complemento     VARCHAR(80),
    bairro          VARCHAR(80),
    cidade          VARCHAR(80)  NOT NULL,
    uf              VARCHAR(2)   NOT NULL,
    cep             VARCHAR(10),
    referencia      TEXT,
    principal       BOOLEAN      NOT NULL DEFAULT FALSE,
    ativo           BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMP    NOT NULL,
    atualizado_em   TIMESTAMP    NOT NULL,
    criado_por      VARCHAR(60),
    atualizado_por  VARCHAR(60)
);

CREATE INDEX idx_endereco_cliente_cliente_id ON endereco_cliente (cliente_id);
CREATE INDEX idx_endereco_cliente_principal  ON endereco_cliente (cliente_id, principal)
    WHERE principal = TRUE AND ativo = TRUE;
CREATE INDEX idx_endereco_cliente_ativo      ON endereco_cliente (cliente_id, ativo);

-- 2. Backfill — só migra clientes com pelo menos logradouro + cidade + uf
--    preenchidos (campos NOT NULL no destino). Clientes com endereço parcial
--    (ex.: só CEP) são preservados sem endereço — o usuário pode recadastrar
--    pelo novo endpoint /clientes/{id}/enderecos.
INSERT INTO endereco_cliente (
    cliente_id, rotulo, logradouro, numero, complemento, bairro, cidade, uf,
    cep, referencia, principal, ativo, criado_em, atualizado_em, criado_por,
    atualizado_por
)
SELECT
    c.id,
    'Principal',
    c.endereco_logradouro,
    c.endereco_numero,
    c.endereco_complemento,
    c.endereco_bairro,
    c.endereco_cidade,
    c.endereco_uf,
    c.endereco_cep,
    NULL,
    TRUE,
    TRUE,
    c.criado_em,
    c.atualizado_em,
    c.criado_por,
    c.atualizado_por
FROM cliente c
WHERE c.endereco_logradouro IS NOT NULL
  AND c.endereco_cidade     IS NOT NULL
  AND c.endereco_uf         IS NOT NULL;

-- 3. Dropa as colunas embutidas de cliente
ALTER TABLE cliente DROP COLUMN endereco_logradouro;
ALTER TABLE cliente DROP COLUMN endereco_numero;
ALTER TABLE cliente DROP COLUMN endereco_complemento;
ALTER TABLE cliente DROP COLUMN endereco_bairro;
ALTER TABLE cliente DROP COLUMN endereco_cidade;
ALTER TABLE cliente DROP COLUMN endereco_uf;
ALTER TABLE cliente DROP COLUMN endereco_cep;
