package org.kduda.greedy.testing.golden

import de.siegmar.fastcsv.reader.CsvReader
import java.nio.file.Files
import java.nio.file.Path

data class InputTable(
	val columns: List<String>,
	val rows: List<List<String>>,
)

/** Reader of golden input files: RFC 4180 CSV, UTF-8 with an optional BOM, every value a string. */
object InputTables {
	private const val BOM_CODE = 0xFEFF

	fun read(path: Path): InputTable {
		val text = Files.readString(path).removePrefix(BOM_CODE.toChar().toString())
		val records =
			CsvReader.builder().ofCsvRecord(text).use { reader ->
				reader.stream().map { it.fields.toList() }.toList()
			}
		require(records.isNotEmpty()) { "empty file: $path" }
		return InputTable(records.first(), records.drop(1))
	}
}
