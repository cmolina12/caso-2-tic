
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Simulador {

    public static void main(String[] args) {

        // SE ASUME QUE EL ARCHIVO CONFIG.TXT SIEMPRE TENDRA EL FORMATO CORRECTO, ESTA
        // EN LA MISMA CARPETA QUE EL PROGRAMA Y NO ESTA VACIO

        // Variables
        String configPath = args[0]; // Ruta del archivo de configuracion

        int TP = 0; // Tamaño de pagina
        int NPROC = 0; // Numero de procesos, en realidad los necesitamos para la opcion 2 por ahora no sirve la vd
        String[] tams = new String[0]; // Tamaños de matrices por proceso (ejemplo: [2x2, 4x4])

        // Leer el archivo de configuracion

        try (BufferedReader br = new BufferedReader(new FileReader(configPath))) {

            String line; // Linea leida del archivo

            while ((line = br.readLine()) != null) {

                line = line.trim(); // Eliminar espacios en blanco al inicio y al final
                if (line.isEmpty())
                    continue; // Saltar lineas vacias

                String[] parts = line.split("="); // Dividir la linea en partes
                String key = parts[0].trim(); // Clave
                String value = parts[1].trim(); // Valor

                if (key.equals("TP")) {
                    TP = Integer.parseInt(value); // Convertir valor a entero
                } else if (key.equals("NPROC")) {
                    NPROC = Integer.parseInt(value); // Convertir valor a entero
                } else if (key.equals("TAMS")) {
                    tams = value.split(","); // Dividir el valor en un array de strings

                }
                ;

            }

        } catch (IOException e) {
            e.printStackTrace(); // Manejo de errores
            return; // Salir del programa en caso de error
        }

        try {
            for (int p = 0; p < tams.length; p++) {
                // Parsear NF y NC de cada tam (NFxNC)
                String[] dims = tams[p].split("x");
                int NF = Integer.parseInt(dims[0]); // Numero de filas
                int NC = Integer.parseInt(dims[1]); // Numero de columnas

                // Calcular cabecera
                long NR = 3L * NF * NC; // Numero total de referencias que hara el proceso (dos read, un write por cada
                                        // element de la matriz)
                long bytePorMatriz = 4L * NF * NC; // Bytes que ocupa cada matriz
                long T = 3L * bytePorMatriz; // Total de bytes que ocupara el proceso (tres matrices, las dos de read y
                                             // la que se escribe)
                long NP = (T + TP - 1) / TP; // Redondea hacia arriba la division entera SIN

                // Escribir archivo proc<i>.txt (solo cabecera para probar)
                try (java.io.PrintWriter pw = new java.io.PrintWriter(
                        new java.io.BufferedWriter(
                                new java.io.FileWriter("proc" + p + ".txt")))) {

                    pw.println("TP=" + TP);
                    pw.println("NF=" + NF);
                    pw.println("NC=" + NC);
                    pw.println("NR=" + NR);
                    pw.println("NP=" + NP);
                    // (AÚN no imprimimos M1/M2/M3)

                    // Ahora si imprimomos las matrices
                    long baseM1 = 0L; // Base de M1, siempre 0
                    long baseM2 = bytePorMatriz; // Base de M2, despues de M1
                    long baseM3 = 2L * bytePorMatriz; // Base de M3, despues de M2
                    for (int i = 0; i < NF; i++) {
                        for (int j = 0; j < NC; j++) {

                            long offset = ((long) i * NC + j) * 4; // Donde esta el elemento (i,j) en bytes dentro de su
                                                                   // matriz

                            // M1, matriz de lectura 1

                            long offsetM1 = baseM1 + offset; // Offset de M1, sera el mismo offset + base
                            pw.println("M1:[" + i + "-" + j + "]," + (offsetM1 / TP) + "," + (offsetM1 % TP) + ",r");

                            // M2, matriz de lectura 2

                            long offsetM2 = baseM2 + offset; // Offset
                            pw.println("M2:[" + i + "-" + j + "]," + (offsetM2 / TP) + "," + (offsetM2 % TP) + ",r");

                            // M3, matriz de escritura

                            long offsetM3 = baseM3 + offset; // Offset
                            pw.println("M3:[" + i + "-" + j + "]," + (offsetM3 / TP) + "," + (offsetM3 % TP) + ",w");

                        }
                    }
                }

                System.out.println("Cabecera generada: proc" + p + ".txt");

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}