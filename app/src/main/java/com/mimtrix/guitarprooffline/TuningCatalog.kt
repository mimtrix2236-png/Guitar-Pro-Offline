package com.mimtrix.guitarprooffline

data class StringSpec(val number: Int, val note: String, val midi: Int, val solfege: String) {
    fun frequency(a4: Double = 440.0) = TunerEngine.frequencyForMidi(midi, a4)
}
data class TuningPreset(val instrument: String, val name: String, val strings: List<StringSpec>)

object TuningCatalog {
    private fun s(number: Int, note: String, midi: Int, solfege: String) = StringSpec(number, note, midi, solfege)
    private fun preset(instrument: String, name: String, vararg strings: StringSpec) = TuningPreset(instrument, name, strings.toList())
    val all = listOf(
        preset("Guitarra", "Estándar E", s(6,"E2",40,"Mi2"),s(5,"A2",45,"La2"),s(4,"D3",50,"Re3"),s(3,"G3",55,"Sol3"),s(2,"B3",59,"Si3"),s(1,"E4",64,"Mi4")),
        preset("Guitarra", "Drop D", s(6,"D2",38,"Re2"),s(5,"A2",45,"La2"),s(4,"D3",50,"Re3"),s(3,"G3",55,"Sol3"),s(2,"B3",59,"Si3"),s(1,"E4",64,"Mi4")),
        preset("Guitarra", "Eb estándar", s(6,"Eb2",39,"Mib2"),s(5,"Ab2",44,"Lab2"),s(4,"Db3",49,"Reb3"),s(3,"Gb3",54,"Solb3"),s(2,"Bb3",58,"Sib3"),s(1,"Eb4",63,"Mib4")),
        preset("Guitarra", "D estándar", s(6,"D2",38,"Re2"),s(5,"G2",43,"Sol2"),s(4,"C3",48,"Do3"),s(3,"F3",53,"Fa3"),s(2,"A3",57,"La3"),s(1,"D4",62,"Re4")),
        preset("Guitarra", "DADGAD", s(6,"D2",38,"Re2"),s(5,"A2",45,"La2"),s(4,"D3",50,"Re3"),s(3,"G3",55,"Sol3"),s(2,"A3",57,"La3"),s(1,"D4",62,"Re4")),
        preset("Guitarra", "Open G", s(6,"D2",38,"Re2"),s(5,"G2",43,"Sol2"),s(4,"D3",50,"Re3"),s(3,"G3",55,"Sol3"),s(2,"B3",59,"Si3"),s(1,"D4",62,"Re4")),
        preset("Guitarra", "Open D", s(6,"D2",38,"Re2"),s(5,"A2",45,"La2"),s(4,"D3",50,"Re3"),s(3,"F#3",54,"Fa#3"),s(2,"A3",57,"La3"),s(1,"D4",62,"Re4")),
        preset("Guitarra", "7 cuerdas", s(7,"B1",35,"Si1"),s(6,"E2",40,"Mi2"),s(5,"A2",45,"La2"),s(4,"D3",50,"Re3"),s(3,"G3",55,"Sol3"),s(2,"B3",59,"Si3"),s(1,"E4",64,"Mi4")),
        preset("Bajo", "Estándar 4", s(4,"E1",28,"Mi1"),s(3,"A1",33,"La1"),s(2,"D2",38,"Re2"),s(1,"G2",43,"Sol2")),
        preset("Bajo", "Estándar 5", s(5,"B0",23,"Si0"),s(4,"E1",28,"Mi1"),s(3,"A1",33,"La1"),s(2,"D2",38,"Re2"),s(1,"G2",43,"Sol2")),
        preset("Bajo", "Estándar 6", s(6,"B0",23,"Si0"),s(5,"E1",28,"Mi1"),s(4,"A1",33,"La1"),s(3,"D2",38,"Re2"),s(2,"G2",43,"Sol2"),s(1,"C3",48,"Do3")),
        preset("Bajo", "Drop D", s(4,"D1",26,"Re1"),s(3,"A1",33,"La1"),s(2,"D2",38,"Re2"),s(1,"G2",43,"Sol2")),
        preset("Ukelele", "Estándar GCEA", s(4,"G4",67,"Sol4"),s(3,"C4",60,"Do4"),s(2,"E4",64,"Mi4"),s(1,"A4",69,"La4")),
        preset("Ukelele", "Barítono", s(4,"D3",50,"Re3"),s(3,"G3",55,"Sol3"),s(2,"B3",59,"Si3"),s(1,"E4",64,"Mi4")),
        preset("Violín", "Estándar", s(4,"G3",55,"Sol3"),s(3,"D4",62,"Re4"),s(2,"A4",69,"La4"),s(1,"E5",76,"Mi5")),
        preset("Viola", "Estándar", s(4,"C3",48,"Do3"),s(3,"G3",55,"Sol3"),s(2,"D4",62,"Re4"),s(1,"A4",69,"La4"))
    )
}
