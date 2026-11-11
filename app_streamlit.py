"""Frequency Hierarchy App (Streamlit): abas Tecnico e Consciencia. Rodar: streamlit run app_streamlit.py"""
import os
from datetime import date

import streamlit as st

from app.core import consciencia as C
from app.entrada import simulador
from app.entrada.csi_receiver import Receptor, resumo
from app.ui import arvore

ARQ = os.path.join(os.path.expanduser("~"), ".freq_hierarchy", "consciencia.json")

st.set_page_config(page_title="Frequency Hierarchy", layout="centered")
st.title("Frequency Hierarchy")
aba_tec, aba_con = st.tabs(["Corpo / sinal (Técnico)", "Consciência / nível"])

with aba_tec:
    st.caption("Mede o CORPO (movimento e respiração) por sinal Wi-Fi. Não mede consciência, alma ou energia espiritual.")
    modo = st.radio("Modo", ["Cenários simulados", "CSI ao vivo (ESP32, UDP 5005)"], horizontal=True)
    if modo.startswith("Cenários"):
        nome = st.selectbox("Cenário", list(simulador.CENARIOS))
        r, a = simulador.rodar(nome)
        st.markdown(arvore.html(r), unsafe_allow_html=True)
        st.write("Sinal: **%s** | f_resp %.2f Hz | f_mov %.2f Hz | regularidade %.2f  (SIMULADO)" % (a.tipo_sinal, a.f_resp, a.f_mov, a.regularidade))
    else:
        st.info("O celular/PC não lê CSI bruto sozinho: o ESP32 mede e envia por UDP para o IP deste aparelho. "
                "Níveis N2-N5 aparecem como 'sem sensor' (não inventamos valores).")
        if st.button("Escutar 5 segundos"):
            rx = Receptor(5005)
            try:
                p = rx.receber(5.0)
            finally:
                rx.fechar()
            if not p:
                st.warning("Sem pacotes. Confira o IP e o Wi-Fi do ESP32.")
            else:
                r, tipo = resumo(p)
                st.markdown(arvore.html(r), unsafe_allow_html=True)
                st.write("Sinal rapido: **%s** | RSSI %d" % (tipo, p["rssi"]))
                s = C.sugestao_pausa(tipo, tipo == "respiracao")
                if s:
                    st.info(s)
                st.caption(C.pergunta_pos_sessao())

with aba_con:
    est = C.carregar(ARQ)
    info = C.info_nivel(est.nivel)
    m = C.metricas(est)
    st.subheader("Nível %d de 7: %s" % (info["nivel"], info["nome"]))
    st.write(info["descricao"])
    st.progress(min(1.0, m["progresso"] / 100.0), text="Progresso no nível: %.0f%%" % m["progresso"])
    st.caption("Práticas cumpridas: %d/%d | dias com check-in (7d): %d/%d | média (7d): %.2f | sequência: %d dia(s)" % (
        m["praticas"], C.PRATICAS_PARA_SUBIR, m["dias7"], C.DIAS_PARA_SUBIR, m["media7"], C.sequencia(est)))
    st.info("Microprática do seu nível: " + info["pratica"])
    st.write("Caminho: " + "  >  ".join(("[%d %s]" % (x["nivel"], x["nome"])) if x["estado"] == "atual" else
                                       ("%d ok" % x["nivel"]) if x["estado"] == "concluido" else str(x["nivel"]) for x in C.caminho(est)))
    with st.form("checkin"):
        st.markdown("**Check-in de hoje** (~30 s)")
        re_ = st.slider("Hoje eu reagi no automático com que frequência? (1 = quase nunca, 5 = quase sempre)", 1, 5, 3)
        pa = st.radio("Consegui pausar antes de responder em algum momento?", ["sim", "não"], horizontal=True) == "sim"
        pr = st.radio("Fiz a microprática do meu nível?", ["sim", "não"], horizontal=True) == "sim"
        nota = st.text_input("Como você está por dentro agora? (opcional)")
        if st.form_submit_button("Registrar check-in"):
            res = C.checkin(est, re_, pa, pr, nota=nota)
            C.salvar(est, ARQ)
            st.success(res["mensagem"] + " (score do dia: %.2f)" % res["score"])
            st.rerun() if res["subiu"] else None
    with st.expander("Ajustar meu nível (autoavaliação)"):
        novo = st.number_input("Nível", 1, 7, est.nivel)
        if st.button("Confirmar nível"):
            try:
                C.ajustar_nivel(est, int(novo))
                C.salvar(est, ARQ)
                st.success("Nível atualizado.")
            except ValueError as e:
                st.error(str(e))
    h = C.historico(est, 30)
    if h:
        st.markdown("**Historico (30 dias)**")
        st.dataframe([{"data": x["data"], "nível": x["nivel"], "score": x["score"], "prática": "sim" if x["pratica"] else "não",
                       "nota": x.get("nota", "")} for x in reversed(h)], use_container_width=True)
    st.caption(C.AVISO)
    st.caption(C.AVISO_CUIDADO)
    st.caption("Seus dados ficam só neste aparelho (%s). Nada é enviado." % ARQ)
