"""EIXO HUMANO: níveis de consciência 1..7 por PRÁTICA e autoavaliação.
O nível NÃO vem do CSI/Wi-Fi: o radar mede o corpo (movimento/respiração), não a consciência.
Sobe-se de nível cumprindo as práticas do nível atual com consistência (check-in diário)."""
import json
import os
from dataclasses import dataclass, field, asdict
from datetime import date, timedelta
from typing import List, Optional

AVISO = "Este nível é autoavaliação e prática. O radar CSI mede o corpo, não a consciência."
AVISO_CUIDADO = ("Não é terapia nem diagnóstico. Se estiver sofrendo muito, converse com alguém de confiança "
                 "ou procure um profissional de saúde.")

NIVEIS = (
    (1, "Reação",          "Vive no automático; pouco registra emoção ou gatilho.",
     "3 vezes ao dia, pare 10 segundos e nomeie: o que estou sentindo?", "Portal da Pausa"),
    (2, "Percepção",       "Começa a notar a emoção e o gatilho depois do fato.",
     "Diário de 1 linha: gatilho → emoção.", "Portal do Nomear"),
    (3, "Observação",      "Observa o padrão sem se fundir totalmente nele.",
     "Pausa de 30 segundos antes de responder em um conflito.", "Portal da Testemunha"),
    (4, "Escolha",         "Consegue escolher a resposta em vez de só reagir.",
     "1 escolha consciente por dia (registre qual).", "Portal da Escolha"),
    (5, "Responsabilidade", "Assume a parte que é sua; menos culpa só no externo.",
     "Em 1 situação do dia, pergunte: o que é meu aqui?", "Portal do Meu"),
    (6, "Coerência",       "Pensamento, fala e ação mais alinhados.",
     "1 ato alinhado ao seu valor principal do dia.", "Portal do Alinhar"),
    (7, "Sabedoria",       "Mais estável; usa a consciência para o bem próprio e do outro.",
     "1 ação útil e discreta por dia + revisão semanal (manutenção, não um fim).", "Portal do Serviço"),
)
AVISO_PORTAIS = "Portais são práticas de consciência, não magia de rede."
AVISO_ATENCAO = "Atenção visual é treino. Não lemos pensamentos."
NIVEL_MAX = 7

DIAS_PARA_SUBIR = 5          # dias COM A PRATICA do nivel nos ultimos 7
MEDIA_PARA_SUBIR = 0.6       # media do score diario nos ultimos 7 dias
# Subir de nivel e SUGESTAO: o app so indica; quem confirma o avanco e a pessoa.


@dataclass
class Checkin:
    data: str                # AAAA-MM-DD
    reacao: int              # 1..5 (quanto reagiu no automatico: 1 = quase nunca, 5 = quase sempre)
    pausou: bool
    pratica: bool
    nivel: int
    score: float = 0.0
    nota: str = ""           # "Como você está por dentro agora?" (opcional, texto livre)


@dataclass
class Estado:
    nivel: int = 1
    entrada_nivel: str = ""  # data em que entrou no nivel atual
    checkins: List[dict] = field(default_factory=list)
    flows: List[dict] = field(default_factory=list)

    def to_dict(self):
        return asdict(self)

    @staticmethod
    def from_dict(d):
        e = Estado(int(d.get("nivel", 1)), d.get("entrada_nivel", ""), list(d.get("checkins", [])), list(d.get("flows", [])))
        e.nivel = max(1, min(NIVEL_MAX, e.nivel))
        return e


def info_nivel(n: int):
    n = max(1, min(NIVEL_MAX, int(n)))
    num, nome, desc, pratica, portal = NIVEIS[n - 1]
    return {"nivel": num, "nome": nome, "descricao": desc, "pratica": pratica, "portal": portal}


def score_do_dia(reacao: int, pausou: bool, pratica: bool) -> float:
    """0..1: 40% menos reacao automatica, 30% pausou, 30% fez a micropratica."""
    r = (5 - max(1, min(5, int(reacao)))) / 4.0
    return round(0.4 * r + 0.3 * (1.0 if pausou else 0.0) + 0.3 * (1.0 if pratica else 0.0), 3)


def _d(s: str) -> date:
    return date.fromisoformat(s)


def _no_nivel(e: Estado):
    return [c for c in e.checkins if c["nivel"] == e.nivel and (not e.entrada_nivel or c["data"] >= e.entrada_nivel)]


def _ultimos7(lst, hoje: date):
    ini = (hoje - timedelta(days=6)).isoformat()
    return [c for c in lst if ini <= c["data"] <= hoje.isoformat()]


def metricas(e: Estado, hoje: Optional[date] = None) -> dict:
    hoje = hoje or date.today()
    lst = _no_nivel(e)
    u7 = _ultimos7(lst, hoje)
    praticas = sum(1 for c in lst if c["pratica"])
    dias7 = len({c["data"] for c in u7})
    dias_pratica7 = len({c["data"] for c in u7 if c["pratica"]})
    media7 = sum(c["score"] for c in u7) / len(u7) if u7 else 0.0
    prog = min(dias_pratica7 / DIAS_PARA_SUBIR, 1.0) * min(media7 / MEDIA_PARA_SUBIR, 1.0)
    return {"praticas": praticas, "dias7": dias7, "dias_pratica7": dias_pratica7, "media7": round(media7, 3),
            "progresso": round(100 * prog, 1),
            "pode_subir": e.nivel < NIVEL_MAX and dias_pratica7 >= DIAS_PARA_SUBIR and media7 >= MEDIA_PARA_SUBIR}


def sequencia(e: Estado, hoje: Optional[date] = None) -> int:
    """Dias seguidos com check-in ate hoje (ou ate ontem). Faltar um dia NAO apaga o progresso do nivel."""
    hoje = hoje or date.today()
    dias = {c["data"] for c in e.checkins}
    d, n = hoje, 0
    if d.isoformat() not in dias:
        d -= timedelta(days=1)
    while d.isoformat() in dias:
        n += 1
        d -= timedelta(days=1)
    return n


def checkin(e: Estado, reacao: int, pausou: bool, pratica: bool, hoje: Optional[date] = None, nota: str = "") -> dict:
    """Registra o check-in do dia (um por dia: refazer substitui). NAO sobe de nivel sozinho: so sinaliza `pode_subir`;
    quem confirma o avanco e a pessoa (ajustar_nivel)."""
    hoje = hoje or date.today()
    if not e.entrada_nivel:
        e.entrada_nivel = hoje.isoformat()
    e.checkins = [c for c in e.checkins if c["data"] != hoje.isoformat()]
    c = Checkin(hoje.isoformat(), max(1, min(5, int(reacao))), bool(pausou), bool(pratica), e.nivel,
                score_do_dia(reacao, pausou, pratica), str(nota)[:280])
    e.checkins.append(asdict(c))
    e.checkins = e.checkins[-400:]
    m = metricas(e, hoje)
    if m["pode_subir"]:
        msg = "Suas práticas indicam que você pode avançar para o nível %d. Confirme quando sentir que faz sentido." % (e.nivel + 1)
    elif e.nivel == NIVEL_MAX:
        msg = "Nível máximo: mantenha a prática."
    else:
        msg = "Check-in registrado."
    return {"score": c.score, "pode_subir": m["pode_subir"], "nivel": e.nivel, "metricas": m, "mensagem": msg}


def ajustar_nivel(e: Estado, novo: int, hoje: Optional[date] = None) -> Estado:
    """Autoavaliacao final do usuario. Pode descer a qualquer nivel; so sobe 1 por vez e so com as praticas em dia
    (nao da para simplesmente marcar o nivel 7)."""
    hoje = hoje or date.today()
    novo = int(novo)
    if novo < 1 or novo > NIVEL_MAX:
        raise ValueError("nível entre 1 e 7")
    if novo == e.nivel:
        return e
    if novo > e.nivel:
        if novo != e.nivel + 1:
            raise ValueError("só é possível subir 1 nível por vez")
        if not metricas(e, hoje)["pode_subir"]:
            raise ValueError("faltam práticas do nível atual para subir")
    e.nivel, e.entrada_nivel = novo, hoje.isoformat()
    return e


def caminho(e: Estado):
    """Lista do caminho ate o 7: concluido / atual / futuro."""
    return [{"nivel": n, "nome": nome, "portal": portal,
             "estado": "concluido" if n < e.nivel else "atual" if n == e.nivel else "futuro"}
            for n, nome, _, _, portal in NIVEIS]


def historico(e: Estado, dias: int = 30, hoje: Optional[date] = None):
    hoje = hoje or date.today()
    ini = (hoje - timedelta(days=max(1, dias) - 1)).isoformat()
    return sorted((c for c in e.checkins if c["data"] >= ini), key=lambda c: c["data"])


def sugestao_pausa(tipo_sinal: str, pessoa_quieta: bool) -> Optional[str]:
    """Ligacao opcional com o eixo tecnico: so SUGESTAO de comportamento, nunca 'seu nivel pelo Wi-Fi'."""
    if tipo_sinal == "respiracao" and pessoa_quieta:
        return "Sua respiração parece estável e você está quieto: que tal uma pausa de 30 segundos para perceber como você está?"
    return None


def pergunta_pos_sessao() -> str:
    return "Como você está por dentro agora?"


# ---- MODO FLOW: treina CONDICOES de atencao engajada; nao induz estado nenhum
FLOW_CONDICOES = (
    "Uma tarefa só (a do seu portal de hoje).",
    "Meta clara e curta (5 a 15 minutos).",
    "Feedback imediato: no fim, você marca se fez ou não.",
    "Pouca distração: silencie as notificações.",
    "Atenção: use o cronômetro e volte ao foco quando se perceber disperso.",
)


def foco_do_nivel(n: int) -> str:
    n = max(1, min(NIVEL_MAX, int(n)))
    if n <= 2:
        return "Reduzir o automático e a dispersão: pare, nomeie o que sente e volte à tarefa."
    if n <= 4:
        return "Uma coisa só, com escolha: decida o que fará agora e deixe o resto de lado."
    if n <= 6:
        return "Responsabilidade e alinhamento na tarefa: faça a sua parte com clareza."
    return "Ação clara a serviço, sem drama."


def registrar_flow(e: Estado, minutos: int, pratica_concluida: bool, absorcao: int,
                   hoje: Optional[date] = None, tarefa: str = "") -> dict:
    """Registra uma sessao. `boa_absorcao` = pratica concluida + autoavaliacao >= 4. Nunca afirma que 'entrou em flow'."""
    hoje = hoje or date.today()
    reg = {"data": hoje.isoformat(), "nivel": e.nivel, "minutos": max(1, min(120, int(minutos))),
           "pratica": bool(pratica_concluida), "absorcao": max(1, min(5, int(absorcao))), "tarefa": str(tarefa)[:120]}
    reg["boa_absorcao"] = reg["pratica"] and reg["absorcao"] >= 4
    e.flows.append(reg)
    e.flows = e.flows[-200:]
    return {"registro": reg, "mensagem": "Sessão com boa absorção relatada." if reg["boa_absorcao"] else "Sessão registrada."}


def resumo_flow(e: Estado, dias: int = 30, hoje: Optional[date] = None) -> dict:
    hoje = hoje or date.today()
    ini = (hoje - timedelta(days=max(1, dias) - 1)).isoformat()
    lst = [f for f in e.flows if f["data"] >= ini]
    return {"sessoes": len(lst), "boa_absorcao": sum(1 for f in lst if f["boa_absorcao"]),
            "minutos": sum(f["minutos"] for f in lst)}


# ---- persistencia local (so neste aparelho)
def carregar(caminho_arq: str) -> Estado:
    try:
        with open(caminho_arq, encoding="utf-8") as f:
            return Estado.from_dict(json.load(f))
    except Exception:
        return Estado()


def salvar(e: Estado, caminho_arq: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(caminho_arq)), exist_ok=True)
    tmp = caminho_arq + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(e.to_dict(), f, ensure_ascii=False)
    os.replace(tmp, caminho_arq)
