# Contador de Trocas — Android Kotlin

App Android **nativo** em Kotlin + Jetpack Compose para controlar pessoas que trocaram notas de R$ 5 e R$ 10.

## Cadastro e regras
- Informe **nome, data, quantidade de notas de R$ 5 e quantidade de notas de R$ 10**.
- O valor é calculado automaticamente: **5 × notas de 5 + 10 × notas de 10**.
- **Não existe quantidade mínima de cada tipo de nota:** só notas de R$ 5, só notas de R$ 10, ou misturadas são aceitas. O critério para presente é exclusivamente o **valor mensal total ≥ R$ 150,00**.
- **A cada novo mês o painel, ranking e elegibilidade começam zerados automaticamente.** Nada do mês anterior é somado à campanha atual. Dentro do mesmo mês, as trocas de uma pessoa somam para atingir R$ 150.
- **Histórico:** os registros e sorteios dos meses encerrados continuam disponíveis para consulta (incluindo totais de cada mês e total histórico), sem entrar nos cálculos do mês atual.
- **R$ 150,00 exatos já tornam a pessoa elegível:** total mensal **≥ R$ 150,00**.
- Ranking mensal, lista de elegíveis, sorteio com uma tentativa por pessoa por mês, histórico de sorteios e confirmação de entrega.
- Presentes iniciais: caixa de bombom, trufa, bolo de pote, desodorante, kit sabonete e escova de dente. Cadastro, edição e exclusão.
- Backup JSON exportável e importável pelas ferramentas de arquivos do Android.

## Executar
1. Abra o repositório no **Android Studio**.
2. Sincronize o Gradle (JDK 17 ou superior, SDK Android 36). Versão alvo Gradle 8.14.3.
3. Execute o módulo `app` em um dispositivo Android 8+ ou emulador.

No GitHub, o workflow **Android CI** faz testes e tenta gerar o APK em **Actions → Android CI → Artifacts → ContadorTroco-debug-apk**. Ele ficará disponível se o build passar. Não é necessário Railway.

Os registros são **locais**, sem login nem sincronização entre aparelhos. Exporte backups com frequência. O backup inclui nomes e valores, não o publique em repositórios públicos.
