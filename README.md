# Simulador de Rede de Filas - T1

Trabalho da disciplina **Simulação e Métodos Analíticos**  
**UA2 | Fundamentos de Simulação por Computador**  
**M8 | Desenvolvimento de Simulador para Rede de Filas**

## Descrição

Este projeto implementa um **simulador de rede de filas** em Java, baseado nos pseudocódigos e conceitos apresentados nos módulos do Moodle.

O simulador foi desenvolvido para suportar:

- leitura de um modelo de rede de filas a partir de um arquivo `.yml`
- tratamento de eventos de:
  - `CHEGADA`
  - `SAIDA`
  - `PASSAGEM`
- roteamento probabilístico entre filas
- filas com diferentes quantidades de servidores
- filas com capacidade finita ou infinita
- critério de parada por quantidade de números aleatórios utilizados

O modelo validado para a T1 está no arquivo:

- `t1_model_seed1.yml`

---

## Estrutura do projeto

Arquivo principal:

- `QueueSimulator.java`

Arquivo de entrada utilizado para validação da T1:

- `t1_model_seed1.yml`

---

## Requisitos

- Java JDK instalado
- terminal com acesso aos comandos `java` e `javac`

Para conferir:

```bash
java -version
javac -version