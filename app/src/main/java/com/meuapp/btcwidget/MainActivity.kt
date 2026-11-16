package com.meuapp.btcwidget

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Bitcoin Widget instalado!\n\n" +
                "Como adicionar na tela inicial:\n" +
                "1. Volte para a tela inicial\n" +
                "2. Segure o dedo em um espaco vazio\n" +
                "3. Toque em 'Widgets'\n" +
                "4. Escolha 'Bitcoin Widget'\n" +
                "5. Arraste para a tela\n\n" +
                "O valor atualiza automaticamente\n" +
                "a cada 10 minutos.\n\n" +
                "Toque no botao ↻ no canto do\n" +
                "widget para atualizar na hora."
            textSize = 15f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(64, 64, 64, 64)
        })
    }
}
