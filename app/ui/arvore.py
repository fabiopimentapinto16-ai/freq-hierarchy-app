"""Arvore da hierarquia em texto/HTML (cores por nivel). Usado pelo Streamlit."""
from app.data.modelos import ResultadoHierarquia


def linhas(r: ResultadoHierarquia):
    out = []
    for n in r.niveis:
        if not n.disponivel:
            out.append({"id": n.id, "nome": n.nome, "hex": n.hex, "barra": 0.0, "texto": "sem sensor", "valor": None})
        else:
            out.append({"id": n.id, "nome": n.nome, "hex": n.hex, "barra": n.f_integrada,
                        "texto": "local %.2f -> integrada %.2f%s" % (n.f_local, n.f_integrada, " (herdou)" if n.herdou else ""),
                        "valor": n.f_integrada})
    return out


def html(r: ResultadoHierarquia) -> str:
    rot = "SIMULADO" if r.simulado else "AO VIVO"
    partes = ["<div style='font-family:sans-serif'><b>%s</b> &middot; F final %.2f &middot; status: <b>%s</b>" % (rot, r.f_final, r.status)]
    for l in linhas(r):
        w = int(round(100 * l["barra"]))
        partes.append("<div style='margin:6px 0'><div style='font-size:13px'>%s %s: %s</div>"
                      "<div style='background:#ddd;border-radius:4px;height:12px'><div style='width:%d%%;background:%s;height:12px;border-radius:4px'></div></div></div>"
                      % (l["id"], l["nome"], l["texto"], w, l["hex"]))
    partes.append("</div>")
    return "".join(partes)
