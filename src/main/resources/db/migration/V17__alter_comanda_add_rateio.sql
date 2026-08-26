-- =============================================================================
-- V17 — Rateio de Comanda (Sprint 3).
--
-- A hierarquia COMPARTILHADA → INDIVIDUAIS já existia desde a V14
-- (comanda_pai_id). Esta migration só adiciona os campos que o rateio
-- precisa:
--
--   estrategia_rateio   — preenchida na COMPARTILHADA quando o rateio é
--                         aplicado; NULL = ainda não rateada. Funciona
--                         como a flag "rateio aplicado".
--   valor_rateio        — significativo na INDIVIDUAL: valor recebido do
--                         rateio da COMPARTILHADA pai. Soma-se ao
--                         total_liquido de itens próprios (se houver).
--   participante_rateio — significativo na INDIVIDUAL: indica se ela
--                         participa do rateio da COMPARTILHADA pai
--                         (cenário "4 amigos, 3 comeram pizza"). Default
--                         TRUE — o Caixa desmarca antes de aplicar o rateio.
-- =============================================================================

ALTER TABLE comanda
    ADD COLUMN estrategia_rateio   VARCHAR(15),
    ADD COLUMN valor_rateio        NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN participante_rateio BOOLEAN       NOT NULL DEFAULT TRUE;
