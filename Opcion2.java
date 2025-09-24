import java.io.*;
import java.util.*;

class Pagina {
    int numPagina;
    long ultimaReferencia; 
    boolean cargada;

    public Pagina(int numPagina) {
        this.numPagina = numPagina;
        this.cargada = false;
        this.ultimaReferencia = -1;
    }
}

class Proceso {
    int id;
    int tp, nf, nc, nr, np; // parámetros
    List<int[]> referencias; // [paginaVirtual, offset, action]
    Map<Integer, Pagina> tablaPaginas;
    Queue<int[]> colaReferencias;

    // estadísticas
    int hits = 0;
    int fallos = 0;
    int swaps = 0;

    // marcos asignados
    Set<Integer> marcosAsignados;

    Set<int[]> refsQueYaFallaron;

    public Proceso(int id) {
        this.id = id;
        this.tablaPaginas = new HashMap<>();
        this.referencias = new ArrayList<>();
        this.colaReferencias = new LinkedList<>();
        this.marcosAsignados = new HashSet<>();
        this.refsQueYaFallaron = new HashSet<>();
    }

    public void cargarArchivo(String nombreArchivo) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(nombreArchivo));
        String linea;
        while ((linea = br.readLine()) != null) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;
            if (linea.startsWith("TP")) tp = Integer.parseInt(linea.split("=")[1]);
            else if (linea.startsWith("NF")) nf = Integer.parseInt(linea.split("=")[1]);
            else if (linea.startsWith("NC")) nc = Integer.parseInt(linea.split("=")[1]);
            else if (linea.startsWith("NR")) nr = Integer.parseInt(linea.split("=")[1]);
            else if (linea.startsWith("NP")) np = Integer.parseInt(linea.split("=")[1]);
            else if (linea.contains(",")) {
                String[] partes = linea.split(",");
                int pagina = Integer.parseInt(partes[1]);
                int offset = Integer.parseInt(partes[2]);
                int accion = partes[3].equalsIgnoreCase("r") ? 0 : 1; // 0=read, 1=write
                int[] ref = {pagina, offset, accion};
                referencias.add(ref);
                colaReferencias.add(ref);
                if (!tablaPaginas.containsKey(pagina)) {
                    tablaPaginas.put(pagina, new Pagina(pagina));
                }
            }
        }
        br.close();
    }

    public boolean tieneReferencias() {
        return !colaReferencias.isEmpty();
    }

    public int[] siguienteReferencia() {
        return colaReferencias.peek();
    }

    public void consumirReferencia() {
        colaReferencias.poll();
    }
}

class Simulador {
    int marcosTotales;
    int numProcesos;
    List<Proceso> procesos;
    Queue<Proceso> colaProcesos;
    Map<Integer, Integer> marcoAsignado; // marco a paginaVirtual
    int tiempo = 0;

    public Simulador(int marcosTotales, int numProcesos) {
        this.marcosTotales = marcosTotales;
        this.numProcesos = numProcesos;
        procesos = new ArrayList<>();
        colaProcesos = new LinkedList<>();
        marcoAsignado = new HashMap<>();
    }

    public void cargarProcesos() throws IOException {
        for (int i = 0; i < numProcesos; i++) {
            Proceso p = new Proceso(i);
            p.cargarArchivo("proc" + i + ".txt");
            procesos.add(p);
            colaProcesos.add(p);
        }
        int marcosPorProceso = marcosTotales / numProcesos;
        int marco = 0;
        for (Proceso p : procesos) {
            for (int j = 0; j < marcosPorProceso; j++) {
                p.marcosAsignados.add(marco);
                marco++;
            }
        }
    }

    public void ejecutar() {
        while (!colaProcesos.isEmpty()) {
            Proceso p = colaProcesos.poll();
            if (!p.tieneReferencias()) {
                liberarMarcos(p);
                continue;
            }
            int[] ref = p.siguienteReferencia();
            int pagina = ref[0];
            Pagina pag = p.tablaPaginas.get(pagina);
            if (pag.cargada) {
                if (!p.refsQueYaFallaron.contains(ref)) {
                    p.hits++;
                }
                pag.ultimaReferencia = tiempo++;
                p.consumirReferencia();
            } else {
                if (!p.refsQueYaFallaron.contains(ref)) {
                    p.fallos++;
                    p.refsQueYaFallaron.add(ref);
                }
                if (p.marcosAsignados.size() > contarPaginasCargadas(p)) {
                    cargarPagina(p, pag);
                    p.swaps += 1;
                } else if (p.marcosAsignados.size() > 0) {
                    Pagina victima = elegirVictimaLRU(p);
                    if (victima != null) victima.cargada = false;
                    cargarPagina(p, pag);
                    p.swaps += 1;
                }
            }
            if (p.tieneReferencias()) colaProcesos.add(p);
        }
    }

    private int contarPaginasCargadas(Proceso p) {
        int c = 0;
        for (Pagina pg : p.tablaPaginas.values()) if (pg.cargada) c++;
        return c;
    }

    private void cargarPagina(Proceso p, Pagina pag) {
        pag.cargada = true;
        pag.ultimaReferencia = tiempo++;
    }

    private Pagina elegirVictimaLRU(Proceso p) {
        Pagina victima = null;
        for (Pagina pg : p.tablaPaginas.values()) {
            if (pg.cargada) {
                if (victima == null || pg.ultimaReferencia < victima.ultimaReferencia) {
                    victima = pg;
                }
            }
        }
        return victima;
    }

    private void liberarMarcos(Proceso p) {
        Proceso target = null;
        for (Proceso otro : procesos) {
            if (otro.tieneReferencias()) {
                if (target == null || otro.fallos > target.fallos) target = otro;
            }
        }
        if (target != null) {
            target.marcosAsignados.addAll(p.marcosAsignados);
        }
        p.marcosAsignados.clear();
        for (Pagina pg : p.tablaPaginas.values()) pg.cargada = false;
    }

    public void imprimirResultados() {
        for (Proceso p : procesos) {
            int total = p.nr;
            int hitsReales = total - p.fallos;
            double tasaFallos = total == 0 ? 0 : (double) p.fallos / total;
            double tasaExito = total == 0 ? 0 : (double) hitsReales / total;
            System.out.println("Proceso: " + p.id);
            System.out.println("- Num referencias: " + total);
            System.out.println("- Fallas: " + p.fallos);
            System.out.println("- Hits: " + hitsReales);
            System.out.println("- SWAP: " + p.swaps);
            System.out.println("- Tasa fallos: " + String.format("%.4f", tasaFallos));
            System.out.println("- Tasa éxito: " + String.format("%.4f", tasaExito));
        }
    }
}

public class Opcion2 {
    public static void main(String[] args) throws IOException {
        int numProcesos = Integer.parseInt(args[0]);
        int marcosTotales = Integer.parseInt(args[1]);
        Simulador sim = new Simulador(marcosTotales, numProcesos);
        sim.cargarProcesos();
        sim.ejecutar();
        sim.imprimirResultados();
    }
}
