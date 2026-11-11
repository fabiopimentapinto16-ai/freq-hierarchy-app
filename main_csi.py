"""CSI ao vivo: escuta o ESP32 (UDP 5005) e imprime a hierarquia. Uso: python main_csi.py [--porta 5005] [--segundos 60]"""
import argparse
import time

from app.entrada.csi_receiver import Receptor, resumo

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--porta", type=int, default=5005)
    ap.add_argument("--segundos", type=float, default=60.0)
    a = ap.parse_args()
    rx = Receptor(a.porta)
    print("Escutando UDP %d (ESP32 deve enviar para o IP deste computador/celular). Ctrl+C para sair." % rx.porta)
    fim = time.time() + a.segundos
    try:
        while time.time() < fim:
            p = rx.receber(2.0)
            if not p:
                print("... sem pacotes (confira o IP e o Wi-Fi do ESP32)")
                continue
            r, tipo = resumo(p)
            print("F(N1)=%.2f  status=%s  sinal=%s  rssi=%d  [N2-N5: sem sensor]" % (r.f_final, r.status, tipo, p["rssi"]))
            an = rx.analise_janela()
            if an:
                print("   janela: %s  f_resp=%.2f Hz  f_mov=%.2f Hz  regularidade=%.2f" % (an.tipo_sinal, an.f_resp, an.f_mov, an.regularidade))
    except KeyboardInterrupt:
        pass
    finally:
        rx.fechar()
