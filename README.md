# Simulador de Rede de Filas - T1

Trabalho da disciplina **Simulação e Métodos Analíticos**  
**UA2 | Fundamentos de Simulação por Computador**  
**M8 | Desenvolvimento de Simulador para Rede de Filas**

## Descrição

Este projeto implementa um simulador de rede de filas em Java, baseado nos pseudocódigos apresentados nos módulos do Moodle.

O simulador suporta:

- leitura de um modelo a partir de arquivo `.yml`
- eventos de `CHEGADA`, `SAIDA` e `PASSAGEM`
- roteamento probabilístico entre filas
- filas com capacidade finita ou infinita
- critério de parada por quantidade de números aleatórios

## Arquivos principais

- `QueueSimulator.java`
- `t1_model_seed1.yml`

## Requisitos

- Java JDK instalado
- comandos `java` e `javac` disponíveis no terminal

Para verificar:

```bash
java -version
javac -version
Como compilar
javac QueueSimulator.java
Como executar
Windows / PowerShell

Executar no terminal:

java -cp . QueueSimulator t1_model_seed1.yml

Executar e salvar a saída em arquivo:

java -cp . QueueSimulator t1_model_seed1.yml t1_report_java.txt
Modelo validado para a T1

O arquivo t1_model_seed1.yml representa a seguinte rede:

Q1: G/G/1, chegadas entre 2..4, atendimento entre 1..2
Q2: G/G/2/5, atendimento entre 4..6
Q3: G/G/2/10, atendimento entre 5..15

Roteamento:

Q1 -> Q2 com probabilidade 0.8
Q1 -> Q3 com probabilidade 0.2
Q2 -> EXIT com probabilidade 0.2
Q2 -> Q1 com probabilidade 0.3
Q2 -> Q3 com probabilidade 0.5
Q3 -> EXIT com probabilidade 0.3
Q3 -> Q2 com probabilidade 0.7

A simulação foi executada com:

filas inicialmente vazias
primeira chegada em 2.0
100000 números aleatórios
seed = 1
Observações
O código segue a base dos pseudocódigos do Moodle:
NextRandom()
AcumulaTempo(ev)
CHEGADA(ev)
SAIDA(ev)
PASSAGEM(ev)
Usa ArrayList<Fila> para representar a rede de filas
Usa PriorityQueue<Evento> como escalonador
Lê o mesmo formato de .yml mostrado no simulador fornecido no módulo
O modelo utilizado na entrega está no arquivo t1_model_seed1.yml
Repositório

Código-fonte do grupo:

https://github.com/pietrolessa/queue_network_simulator