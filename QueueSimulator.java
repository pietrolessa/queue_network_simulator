import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

public class QueueSimulator {

    // =========================================================
    // Tipos de evento: CHEGADA, SAIDA e PASSAGEM
    // =========================================================
    enum TipoEvento { CHEGADA, SAIDA, PASSAGEM }

    static class AcabouAleatoriosException extends RuntimeException {
        AcabouAleatoriosException(String msg) { super(msg); }
    }

    // =========================================================
    // GERADOR / FONTE DE ALEATÓRIOS
    // Mantido simples, como sugerido nos módulos.
    // Para "seeds", usa o mesmo LCG que reproduziu o simulator.jar.
    // =========================================================
    interface Amostrador {
        boolean hasNext();
        double next();
    }

    static class LCGAmostrador implements Amostrador {
        private static final long A = 25214903917L;
        private static final long C = 11L;
        private static final long MASK = (1L << 48) - 1;

        private final long limite;
        private long previous;
        private long usados;

        LCGAmostrador(long limite, long seed) {
            this.limite = limite;
            this.previous = seed;
            this.usados = 0L;
        }

        public boolean hasNext() {
            return usados < limite;
        }

        public double next() {
            if (!hasNext()) {
                throw new AcabouAleatoriosException("Acabou os números aleatórios!");
            }
            previous = (previous * A + C) & MASK;
            usados++;
            return (double) previous / (double) (MASK + 1L);
        }
    }

    static class ListaAmostrador implements Amostrador {
        private final List<Double> numeros;
        private int indice;

        ListaAmostrador(List<Double> numeros) {
            this.numeros = new ArrayList<>(numeros);
            this.indice = 0;
        }

        public boolean hasNext() {
            return indice < numeros.size();
        }

        public double next() {
            if (!hasNext()) {
                throw new AcabouAleatoriosException("Acabou os números aleatórios!");
            }
            return numeros.get(indice++);
        }
    }

    static class FonteAleatoria {
        private final Amostrador amostrador;

        FonteAleatoria(Amostrador amostrador) {
            this.amostrador = amostrador;
        }

        boolean hasNext() {
            return amostrador.hasNext();
        }

        double NextRandom() {
            return amostrador.next();
        }

        double uniforme(double min, double max) {
            return min + (max - min) * NextRandom();
        }
    }

    // =========================================================
    // ESTRUTURAS DA REDE DE FILAS
    // =========================================================
    static class Rota {
        int destino;      // -1 = exterior
        double probabilidade;

        Rota(int destino, double probabilidade) {
            this.destino = destino;
            this.probabilidade = probabilidade;
        }
    }

    static class Fila {
        int id;
        int servers;
        int capacity;     // -1 = infinita
        double minArrival = -1.0;
        double maxArrival = -1.0;
        double minService;
        double maxService;

        int customers = 0;
        int loss = 0;

        // times[estado] -> tempo acumulado naquele estado
        Map<Integer, Double> times = new TreeMap<>();
        List<Rota> rotas = new ArrayList<>();

        Fila(int id) {
            this.id = id;
            this.capacity = -1;
        }

        int Status() { return customers; }
        int Capacity() { return capacity; }
        int Servers() { return servers; }
        void Loss() { loss++; }
        void In() { customers++; }
        void Out() { customers--; }

        void addRota(int destino, double probabilidade) {
            rotas.add(new Rota(destino, probabilidade));
            rotas.sort(Comparator.comparingDouble(r -> r.probabilidade));
        }
    }

    static class Evento {
        double tempo;
        TipoEvento tipo;
        int origem;   // -1 = exterior
        int destino;  // -1 = exterior
        long ordem;

        Evento(double tempo, TipoEvento tipo, int origem, int destino, long ordem) {
            this.tempo = tempo;
            this.tipo = tipo;
            this.origem = origem;
            this.destino = destino;
            this.ordem = ordem;
        }
    }

    // =========================================================
    // MODELO LIDO DO YAML
    // =========================================================
    static class DefFila {
        int servers;
        int capacity = -1;
        double minArrival = -1.0;
        double maxArrival = -1.0;
        double minService;
        double maxService;
    }

    static class DefRota {
        String source;
        String target;
        double probability;
    }

    static class Modelo {
        LinkedHashMap<String, Double> arrivals = new LinkedHashMap<>();
        LinkedHashMap<String, DefFila> queues = new LinkedHashMap<>();
        List<DefRota> network = new ArrayList<>();
        List<Double> rndnumbers = new ArrayList<>();
        List<Long> seeds = new ArrayList<>();
        Long rndnumbersPerSeed = null;
    }

    // =========================================================
    // VARIÁVEIS GLOBAIS, no estilo dos pseudocódigos do Moodle
    // =========================================================
    static ArrayList<Fila> listaDeFilas = new ArrayList<>();
    static PriorityQueue<Evento> escalonador = new PriorityQueue<>(
            Comparator.comparingDouble((Evento ev) -> ev.tempo)
                    .thenComparingLong(ev -> ev.ordem)
    );
    static LinkedHashMap<Integer, Double> chegadasIniciais = new LinkedHashMap<>();
    static FonteAleatoria gerador;

    static double TG = 0.0;
    static double ultimoTempoEvento = 0.0;
    static long proximaOrdem = 1L;

    // para múltiplas simulações (se houver várias seeds)
    static double tempoTotalAcumulado = 0.0;
    static int numeroDeSimulacoes = 0;

    // =========================================================
    // UTILITÁRIOS
    // =========================================================
    static int nomeFilaParaId(String nome) {
        if (nome == null || nome.equals("EXIT")) return -1;
        if (nome.startsWith("Q")) {
            return Integer.parseInt(nome.substring(1));
        }
        throw new IllegalArgumentException("Nome de fila inválido: " + nome);
    }

    static String idFilaParaNome(int id) {
        return id < 0 ? "EXIT" : ("Q" + id);
    }

    static Fila getFila(int id) {
        for (Fila fila : listaDeFilas) {
            if (fila.id == id) return fila;
        }
        return null;
    }

    static double getTempoChegada(Fila fila) {
        return gerador.uniforme(fila.minArrival, fila.maxArrival);
    }

    static double getTempoServico(Fila fila) {
        return gerador.uniforme(fila.minService, fila.maxService);
    }

    // =========================================================
    // M8: roteamento por probabilidade acumulada
    // =========================================================
    static int EscolheDestino(Fila filaOrigem) {
        if (filaOrigem.rotas.isEmpty()) {
            return -1;
        }
        if (filaOrigem.rotas.size() == 1 && filaOrigem.rotas.get(0).probabilidade >= 1.0) {
            return filaOrigem.rotas.get(0).destino;
        }

        double prob = gerador.NextRandom();
        double restante = prob;
        for (Rota rota : filaOrigem.rotas) {
            if (restante <= rota.probabilidade) {
                return rota.destino;
            }
            restante -= rota.probabilidade;
        }
        return -1;
    }

    // =========================================================
    // ACUMULA TEMPO EM TODAS AS FILAS (módulos 6/7/8)
    // =========================================================
    static void AcumulaTempo(Evento ev) {
        double delta = ev.tempo - TG;
        for (Fila fila : listaDeFilas) {
            int estado = fila.Status();
            fila.times.put(estado, fila.times.getOrDefault(estado, 0.0) + delta);
        }
        TG = ev.tempo;
        ultimoTempoEvento = TG;
    }

    // =========================================================
    // ESCALONAMENTO
    // =========================================================
    static void EscalonarChegada(int destino, Double delta) {
        Fila filaDestino = getFila(destino);
        double intervalo = (delta != null) ? delta : getTempoChegada(filaDestino);
        escalonador.add(new Evento(TG + intervalo, TipoEvento.CHEGADA, -1, destino, proximaOrdem++));
    }

    static void EscalonarSaida(int origem, Double delta) {
        Fila filaOrigem = getFila(origem);
        double intervalo = (delta != null) ? delta : getTempoServico(filaOrigem);
        escalonador.add(new Evento(TG + intervalo, TipoEvento.SAIDA, origem, -1, proximaOrdem++));
    }

    static void EscalonarPassagem(int origem, int destino, Double delta) {
        Fila filaOrigem = getFila(origem);
        double intervalo = (delta != null) ? delta : getTempoServico(filaOrigem);
        escalonador.add(new Evento(TG + intervalo, TipoEvento.PASSAGEM, origem, destino, proximaOrdem++));
    }

    static void EscalonarFimAtendimento(Fila filaOrigem) {
        int destino = EscolheDestino(filaOrigem);
        if (destino == -1) {
            EscalonarSaida(filaOrigem.id, null);
        } else {
            EscalonarPassagem(filaOrigem.id, destino, null);
        }
    }

    // =========================================================
    // PROCEDIMENTOS DE EVENTO
    // =========================================================
    static void CHEGADA(Evento ev, boolean chegadaExterna) {
        AcumulaTempo(ev);

        Fila filaDestino = getFila(ev.destino);

        if (filaDestino.Capacity() < 0 || filaDestino.Status() < filaDestino.Capacity()) {
            filaDestino.In();

            if (filaDestino.Status() <= filaDestino.Servers()) {
                EscalonarFimAtendimento(filaDestino);
            }
        } else {
            filaDestino.Loss();
        }

        // somente chegada externa agenda a próxima chegada externa
        if (chegadaExterna && filaDestino.minArrival >= 0.0 && filaDestino.maxArrival >= 0.0) {
            EscalonarChegada(filaDestino.id, null);
        }
    }

    static void SAIDA(Evento ev) {
        AcumulaTempo(ev);

        Fila filaOrigem = getFila(ev.origem);
        filaOrigem.Out();

        if (filaOrigem.Status() >= filaOrigem.Servers()) {
            EscalonarFimAtendimento(filaOrigem);
        }
    }

    static void PASSAGEM(Evento ev) {
        AcumulaTempo(ev);

        Fila filaOrigem = getFila(ev.origem);
        Fila filaDestino = getFila(ev.destino);

        // parte de saída da fila de origem
        filaOrigem.Out();
        if (filaOrigem.Status() >= filaOrigem.Servers()) {
            EscalonarFimAtendimento(filaOrigem);
        }

        // parte de chegada na fila de destino
        if (filaDestino.Capacity() < 0 || filaDestino.Status() < filaDestino.Capacity()) {
            filaDestino.In();
            if (filaDestino.Status() <= filaDestino.Servers()) {
                EscalonarFimAtendimento(filaDestino);
            }
        } else {
            filaDestino.Loss();
        }
    }

    // =========================================================
    // LOOP PRINCIPAL DA SIMULAÇÃO
    // =========================================================
    static void SimularUmaRodada() {
        try {
            while (gerador.hasNext() && !escalonador.isEmpty()) {
                Evento ev = escalonador.poll();
                if (ev.tipo == TipoEvento.CHEGADA) {
                    CHEGADA(ev, true);
                } else if (ev.tipo == TipoEvento.SAIDA) {
                    SAIDA(ev);
                } else if (ev.tipo == TipoEvento.PASSAGEM) {
                    PASSAGEM(ev);
                }
            }
        } catch (AcabouAleatoriosException ignored) {
            // encerra exatamente quando o 100000º aleatório for usado
        }
    }

    static void ResetarParaNovaRodada() {
        escalonador.clear();
        TG = 0.0;
        ultimoTempoEvento = 0.0;
        proximaOrdem = 1L;

        for (Map.Entry<Integer, Double> chegada : chegadasIniciais.entrySet()) {
            EscalonarChegada(chegada.getKey(), chegada.getValue());
        }

        for (Fila fila : listaDeFilas) {
            fila.customers = 0;
        }
    }

    // =========================================================
    // PARSER SIMPLES DO YAML USADO PELO professor / simulator.jar
    // =========================================================
    static Modelo lerModelo(Path caminho) throws IOException {
        List<String> linhas = Files.readAllLines(caminho);
        Modelo modelo = new Modelo();
        String secao = null;
        String filaAtual = null;
        DefRota rotaAtual = null;

        for (String linhaOriginal : linhas) {
            String linha = stripComment(linhaOriginal);
            if (linha.trim().isEmpty()) continue;
            if (linha.trim().equals("!PARAMETERS")) continue;

            int indent = countLeadingSpaces(linha);
            String t = linha.trim();

            if (indent == 0 && !t.startsWith("-")) {
                filaAtual = null;
                rotaAtual = null;
                if (t.endsWith(":")) {
                    secao = t.substring(0, t.length() - 1).trim();
                } else {
                    String[] kv = splitKeyValue(t);
                    if (kv != null && kv[0].equals("rndnumbersPerSeed")) {
                        modelo.rndnumbersPerSeed = Long.parseLong(kv[1]);
                    }
                }
                continue;
            }

            if ("arrivals".equals(secao)) {
                String[] kv = splitKeyValue(t);
                if (kv != null) {
                    modelo.arrivals.put(kv[0], Double.parseDouble(kv[1]));
                }
                continue;
            }

            if ("queues".equals(secao)) {
                if (indent <= 3 && t.endsWith(":")) {
                    filaAtual = t.substring(0, t.length() - 1).trim();
                    modelo.queues.put(filaAtual, new DefFila());
                } else if (filaAtual != null) {
                    String[] kv = splitKeyValue(t);
                    if (kv != null) {
                        DefFila q = modelo.queues.get(filaAtual);
                        switch (kv[0]) {
                            case "servers" -> q.servers = Integer.parseInt(kv[1]);
                            case "capacity" -> q.capacity = Integer.parseInt(kv[1]);
                            case "minArrival" -> q.minArrival = Double.parseDouble(kv[1]);
                            case "maxArrival" -> q.maxArrival = Double.parseDouble(kv[1]);
                            case "minService" -> q.minService = Double.parseDouble(kv[1]);
                            case "maxService" -> q.maxService = Double.parseDouble(kv[1]);
                        }
                    }
                }
                continue;
            }

            if ("network".equals(secao)) {
                if (t.startsWith("-")) {
                    rotaAtual = new DefRota();
                    modelo.network.add(rotaAtual);
                    String resto = t.substring(1).trim();
                    if (!resto.isEmpty()) {
                        String[] kv = splitKeyValue(resto);
                        if (kv != null) assignRouteField(rotaAtual, kv[0], kv[1]);
                    }
                } else if (rotaAtual != null) {
                    String[] kv = splitKeyValue(t);
                    if (kv != null) assignRouteField(rotaAtual, kv[0], kv[1]);
                }
                continue;
            }

            if ("rndnumbers".equals(secao)) {
                if (t.startsWith("-")) {
                    modelo.rndnumbers.add(Double.parseDouble(t.substring(1).trim()));
                }
                continue;
            }

            if ("seeds".equals(secao)) {
                if (t.startsWith("-")) {
                    modelo.seeds.add(Long.parseLong(t.substring(1).trim()));
                }
            }
        }

        return modelo;
    }

    static void assignRouteField(DefRota rota, String key, String value) {
        switch (key) {
            case "source" -> rota.source = value;
            case "target" -> rota.target = value;
            case "probability" -> rota.probability = Double.parseDouble(value);
        }
    }

    static String stripComment(String line) {
        int idx = line.indexOf('#');
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    static int countLeadingSpaces(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    static String[] splitKeyValue(String text) {
        int idx = text.indexOf(':');
        if (idx < 0) return null;
        String key = text.substring(0, idx).trim();
        String value = text.substring(idx + 1).trim();
        return new String[] { key, value };
    }

    // =========================================================
    // CARGA DO MODELO EM MEMÓRIA
    // =========================================================
    static void CarregarModelo(Modelo modelo) {
        listaDeFilas.clear();
        escalonador.clear();
        chegadasIniciais.clear();
        TG = 0.0;
        ultimoTempoEvento = 0.0;
        proximaOrdem = 1L;
        tempoTotalAcumulado = 0.0;
        numeroDeSimulacoes = 0;

        for (Map.Entry<String, DefFila> entry : modelo.queues.entrySet()) {
            int id = nomeFilaParaId(entry.getKey());
            DefFila def = entry.getValue();
            Fila fila = new Fila(id);
            fila.servers = def.servers;
            fila.capacity = def.capacity;
            fila.minArrival = def.minArrival;
            fila.maxArrival = def.maxArrival;
            fila.minService = def.minService;
            fila.maxService = def.maxService;
            listaDeFilas.add(fila);
        }

        for (Map.Entry<String, Double> entry : modelo.arrivals.entrySet()) {
            chegadasIniciais.put(nomeFilaParaId(entry.getKey()), entry.getValue());
        }

        for (DefRota rota : modelo.network) {
            Fila filaOrigem = getFila(nomeFilaParaId(rota.source));
            int destino = nomeFilaParaId(rota.target);
            filaOrigem.addRota(destino, rota.probability);
        }
    }

    // =========================================================
    // RELATÓRIO
    // =========================================================
    static String cabecalhoFila(Fila fila) {
        if (fila.capacity < 0) {
            return String.format("Queue:   %s (G/G/%d)", idFilaParaNome(fila.id), fila.servers);
        }
        return String.format("Queue:   %s (G/G/%d/%d)", idFilaParaNome(fila.id), fila.servers, fila.capacity);
    }

    static String formatarRelatorio() {
        StringBuilder sb = new StringBuilder();
        sb.append("=========================================================\n");
        sb.append("======================    REPORT   ======================\n");
        sb.append("=========================================================\n");

        double tempoMedio = numeroDeSimulacoes == 0 ? TG : tempoTotalAcumulado / numeroDeSimulacoes;

        for (Fila fila : listaDeFilas) {
            sb.append("*********************************************************\n");
            sb.append(cabecalhoFila(fila)).append("\n");
            if (fila.minArrival >= 0.0) {
                sb.append(String.format(Locale.US, "Arrival: %.1f ... %.1f%n", fila.minArrival, fila.maxArrival));
            }
            sb.append(String.format(Locale.US, "Service: %.1f ... %.1f%n", fila.minService, fila.maxService));
            sb.append("*********************************************************\n");
            sb.append("   State               Time               Probability\n");
            for (Map.Entry<Integer, Double> entry : fila.times.entrySet()) {
                double probability = tempoMedio == 0.0 ? 0.0 : (entry.getValue() / (tempoTotalAcumulado == 0.0 ? TG : tempoTotalAcumulado) * 100.0);
                sb.append(String.format(Locale.US, "%7d%21.4f%21.2f%%%n", entry.getKey(), entry.getValue(), probability));
            }
            sb.append("\nNumber of losses: ").append(fila.loss).append("\n\n");
        }

        sb.append("=========================================================\n");
        sb.append(String.format(Locale.US, "Simulation average time: %.4f%n", tempoMedio));
        sb.append("=========================================================");
        return sb.toString();
    }

    // =========================================================
    // EXECUÇÃO DO MODELO
    // =========================================================
    static void ExecutarModelo(Modelo modelo) {
        CarregarModelo(modelo);

        int simulacoes = modelo.seeds.isEmpty() ? 1 : modelo.seeds.size();
        numeroDeSimulacoes = simulacoes;

        for (int i = 0; i < simulacoes; i++) {
            if (!modelo.seeds.isEmpty()) {
                if (modelo.rndnumbersPerSeed == null) {
                    throw new IllegalArgumentException("rndnumbersPerSeed precisa estar definido quando seeds é usado.");
                }
                gerador = new FonteAleatoria(new LCGAmostrador(modelo.rndnumbersPerSeed, modelo.seeds.get(i)));
            } else {
                gerador = new FonteAleatoria(new ListaAmostrador(modelo.rndnumbers));
            }

            ResetarParaNovaRodada();
            SimularUmaRodada();
            tempoTotalAcumulado += TG;
        }
    }

    static void printBanner() {
        System.out.println("=========================================================");
        System.out.println("==========   SIMULADOR DE REDE DE FILAS (M8)   ==========");
        System.out.println("======  baseado nos pseudocódigos do Moodle / UA2  ======");
        System.out.println("=========================================================");
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);

        if (args.length < 1) {
            System.out.println("uso:");
            System.out.println("  java QueueSimulator <arquivo.yml> [saida.txt]");
            return;
        }

        printBanner();
        Modelo modelo = lerModelo(Path.of(args[0]));
        ExecutarModelo(modelo);
        String relatorio = formatarRelatorio();
        System.out.println(relatorio);

        if (args.length >= 2) {
            Files.writeString(Path.of(args[1]), relatorio);
        }
    }
}
