"""CSI ao vivo: escuta o ESP32 (UDP 5005) e imprime a hierarquia.
Uso: python main_csi.py [--porta 5005] [--segundos 60] [--calibrar 25]
--calibrar N: nos primeiros N segundos mantenha a sala PARADA (ninguem se mexendo); o ruido medido e descontado depois."""
import argparse
import time

from app.core.hierarquia import Suavizador, integrar
from app.core.respiracao import calibrar
from app.entrada.csi_receiver import Receptor, f_locais_do_pacote, resumo
from app.core.respiracao import tipo_rapido

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--porta", type=int, default=5005)
    ap.add_argument("--segundos", type=float, default=60.0)
    ap.add_argument("--calibrar", type=float, default=0.0, help="segundos de calibracao com a sala parada (>= 10)")
    a = ap.parse_args()
    rx = Receptor(a.porta)
    base = 0.0
    print("Escutando UDP %d (ESP32 deve enviar para o IP deste computador/celular). Ctrl+C para sair." % rx.porta)
    try:
        if a.calibrar >= 10:
            print("CALIBRANDO %.0f s: deixe a sala parada, sem ninguem se mexendo..." % a.calibrar)
            fim_cal, amostras = time.time() + a.calibrar, []
            while time.time() < fim_cal:
                p = rx.receber(2.0)
                if p:
                    amostras.append((p["ts"], p["amplitude"]))
            if len(amostras) >= 16 and amostras[-1][0] - amostras[0][0] >= 8:
                fs = (len(amostras) - 1) / (amostras[-1][0] - amostras[0][0])
                base = calibrar([x[1] for x in amostras], fs)
                print("Calibrado: ruido de base = %.4f (sera descontado)." % base)
            else:
                print("Poucos pacotes na calibracao; seguindo sem calibrar.")
            rx.amostras.clear()
        suav = Suavizador(0.3)
        fim = time.time() + a.segundos
        while time.time() < fim:
            p = rx.receber(2.0)
            if not p:
                print("... sem pacotes (confira o IP e o Wi-Fi do ESP32)")
                continue
            r = integrar(suav.atualizar(f_locais_do_pacote(p)))
            tipo = tipo_rapido(p["movimento"], p["respiracao"])
            print("F(N1)=%.2f  status=%s  sinal=%s  rssi=%d  [N2-N5: sem sensor]" % (r.f_final, r.status, tipo, p["rssi"]))
            an = rx.analise_janela(base)
            if an:
                print("   janela: %s  f_resp=%.2f Hz  f_mov=%.2f Hz  regularidade=%.2f" % (an.tipo_sinal, an.f_resp, an.f_mov, an.regularidade))
    except KeyboardInterrupt:
        pass
    finally:
        rx.fechar()
