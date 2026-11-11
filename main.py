"""Roda todos os cenarios simulados no terminal. Uso: python main.py"""
from app.entrada import simulador

if __name__ == "__main__":
    print("CENARIOS SIMULADOS (nao sao medidas reais)\n")
    for nome in simulador.CENARIOS:
        r, a = simulador.rodar(nome)
        print("%-18s F_final=%.2f  status=%-8s  sinal=%-10s f_resp=%.2f f_mov=%.2f" % (
            nome, r.f_final, r.status, a.tipo_sinal, a.f_resp, a.f_mov))
