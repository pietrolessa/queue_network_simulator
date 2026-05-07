# Simulador de Rede de Filas - T1

Arquivo principal: `QueueSimulator.java`

## Como compilar

```bash
javac QueueSimulator.java
```

## Como executar

```bash
java QueueSimulator t1_model_seed1.yml t1_report_java.txt
```

ou, sem salvar em arquivo:

```bash
java QueueSimulator t1_model_seed1.yml
```

## Observações

- O código segue a base dos pseudocódigos do Moodle:
  - `NextRandom()`
  - `AcumulaTempo(ev)`
  - `CHEGADA(ev)`
  - `SAIDA(ev)`
  - `PASSAGEM(ev)`
- Usa `ArrayList<Fila>` para a rede.
- Usa `PriorityQueue<Evento>` como escalonador.
- Lê o mesmo formato de `.yml` mostrado no exemplo do simulador em Java.
- Para a T1, o modelo validado está no arquivo `t1_model_seed1.yml`.
