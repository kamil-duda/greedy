package spike.prepare

import de.siegmar.fastcsv.reader.CsvReader
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/** The app's CSV parser: RFC 4180 (FastCSV), UTF-8, a leading BOM dropped; the header is the first record. */
object CsvRows {
	fun stream(input: InputStream): Sequence<List<String>> {
		val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8), 1 shl 16)
		reader.mark(1)
		if (reader.read() != 0xFEFF) reader.reset()
		return CsvReader.builder().ofCsvRecord(reader).asSequence().map { it.fields }
	}
}
