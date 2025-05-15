package com.example.battle_command

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.battle_command.ui.theme.Battle_CommandTheme

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlin.random.Random


enum class Orientation { HORIZONTAL, VERTICAL }

val shipRegistry = mutableMapOf<Int, List<Pair<Int, Int>>>()
var currentShipId = 0

data class Ship(
    val size: Pair<Int, Int>, // width, height
    val positions: List<Pair<Int, Int>>
)

sealed class TileState {
    object EMPTY : TileState()
    object HIT : TileState()
    object MISS : TileState()
    data class SHIP(val shipId: Int) : TileState()
}



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Battle_CommandTheme {
                val navController = rememberNavController()

                // Shared player grids
                val player1Grid = remember { mutableStateListOf<MutableList<TileState>>() }
                val player2Grid = remember { mutableStateListOf<MutableList<TileState>>() }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { HomeScreen(navController) }

                    composable("shipPlacementPlayer1") {
                        ShipPlacementScreen(
                            navController = navController,
                            playerGrid = player1Grid,
                            onPlacementComplete = { navController.navigate("shipPlacementPlayer2") },
                            playerName = "Player 1"
                        )
                    }

                    composable("shipPlacementPlayer2") {
                        ShipPlacementScreen(
                            navController = navController,
                            playerGrid = player2Grid,
                            onPlacementComplete = { navController.navigate("game") },
                            playerName = "Player 2"
                        )
                    }

                    composable("game") {
                        GameScreen(navController, player1Grid, player2Grid)
                    }
                }
            }
        }

    }
}

@Composable
fun HomeScreen(navController: NavHostController) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Battle Command", fontSize = 32.sp)

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            navController.navigate("shipPlacementPlayer1")
        }) {
            Text("Start Game")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { showDialog = true }) {
            Text("Game Description")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { /* TODO: Show Win History */ }) {
            Text("View Win History")
        }
    }

    // Game Description Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Game Rules") },
            text = {
                Text(
                    "Each player controls a fleet of ships.\n\n" +
                            "- On each turn, choose to either ATTACK or FORTIFY.\n" +
                            "- ATTACK: Target a tile on the opponent's grid.\n" +
                            "- FORTIFY: Move undamaged ships to a new position.\n\n" +
                            "The first player to destroy the opponent's entire fleet wins."
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Got it!")
                }
            }
        )
    }
}


@Composable
fun GameScreen(
    navController: NavHostController,
    player1Grid: List<MutableList<TileState>>,
    player2Grid: List<MutableList<TileState>>
) {
    val gridSize = 6

//    val player1Grid = remember {
//        List(gridSize) { mutableStateListOf(*Array(gridSize) { TileState.EMPTY }) }
//    }
//    val player2Grid = remember {
//        List(gridSize) { mutableStateListOf(*Array(gridSize) { TileState.EMPTY }) }
//    }

    var isPlayer1Turn by remember { mutableStateOf(true) }
    var currentAction by remember { mutableStateOf("Attack") }
    var fortifySource by remember { mutableStateOf<Pair<Int, Int>?>(null) }

//    LaunchedEffect(Unit) {
////        placeRandomShips(player1Grid)
//        placeRandomShips(player2Grid)
//    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Player ${if (isPlayer1Turn) "1" else "2"}'s Turn - $currentAction",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Player 2's grid (Top - opponent view for Player 1)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Player 2's Fleet (Enemy View)", fontWeight = FontWeight.Bold)
            GameGrid(
                gridSize = gridSize,
                grid = player2Grid,
                onTileClick = { row, col ->
                    if (isPlayer1Turn && currentAction == "Attack") {
                        if (player2Grid[row][col] == TileState.SHIP()) player2Grid[row][col] = TileState.HIT
                        else if (player2Grid[row][col] == TileState.EMPTY) player2Grid[row][col] = TileState.MISS
                        isPlayer1Turn = false
                        fortifySource = null
                    } else if (!isPlayer1Turn && currentAction == "Fortify") {
                        handleFortify(player2Grid, row, col, fortifySource) { updatedSource ->
                            fortifySource = updatedSource
                            if (updatedSource == null) isPlayer1Turn = true
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(Modifier.fillMaxWidth().height(2.dp), color = Color.DarkGray)
        Spacer(modifier = Modifier.height(16.dp))

        // Player 1's grid (Bottom - own view)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Player 1's Fleet", fontWeight = FontWeight.Bold)
            GameGrid(
                gridSize = gridSize,
                grid = player1Grid,
                onTileClick = { row, col ->
                    if (!isPlayer1Turn && currentAction == "Attack") {
                        if (player1Grid[row][col] == TileState.SHIP()) player1Grid[row][col] = TileState.HIT
                        else if (player1Grid[row][col] == TileState.EMPTY) player1Grid[row][col] = TileState.MISS
                        isPlayer1Turn = true
                        fortifySource = null
                    } else if (isPlayer1Turn && currentAction == "Fortify") {
                        handleFortify(player1Grid, row, col, fortifySource) { updatedSource ->
                            fortifySource = updatedSource
                            if (updatedSource == null) isPlayer1Turn = false
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = {
                currentAction = if (currentAction == "Attack") "Fortify" else "Attack"
                fortifySource = null
            }) {
                Text("Switch to ${if (currentAction == "Attack") "Fortify" else "Attack"}")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(onClick = { navController.popBackStack() }) {
                Text("Back to Home")
            }
        }
    }
}

fun getConnectedShipTiles(
    grid: List<MutableList<TileState>>,
    row: Int,
    col: Int,
    visited: MutableSet<Pair<Int, Int>> = mutableSetOf()
): List<Pair<Int, Int>> {
    if (row !in grid.indices || col !in grid[0].indices) return emptyList()
    if (grid[row][col] != TileState.SHIP() || Pair(row, col) in visited) return emptyList()

    visited.add(Pair(row, col))
    val positions = mutableListOf(Pair(row, col))

    // Check 4 directions (no diagonal)
    positions += getConnectedShipTiles(grid, row + 1, col, visited)
    positions += getConnectedShipTiles(grid, row - 1, col, visited)
    positions += getConnectedShipTiles(grid, row, col + 1, visited)
    positions += getConnectedShipTiles(grid, row, col - 1, visited)

    return positions
}


fun handleFortify(
    grid: List<MutableList<TileState>>,
    row: Int,
    col: Int,
    source: Pair<Int, Int>?,
    onComplete: (Pair<Int, Int>?) -> Unit
) {
    if (source == null) {
        val tile = grid[row][col]
        if (tile is TileState.SHIP) {
            onComplete(Pair(row, col))
        } else {
            onComplete(null)
        }
    } else {
        val (srcRow, srcCol) = source
        val sourceTile = grid[srcRow][srcCol]
        if (sourceTile !is TileState.SHIP) {
            onComplete(null)
            return
        }

        val shipId = sourceTile.shipId
        val shipTiles = shipRegistry[shipId] ?: return

        val relativeOffsets = shipTiles.map { (r, c) -> r - srcRow to c - srcCol }
        val newPositions = relativeOffsets.map { (dr, dc) -> row + dr to col + dc }

        // Ensure new positions are empty and valid
        if (newPositions.all { (r, c) ->
                r in grid.indices && c in grid[0].indices &&
                        (grid[r][c] == TileState.EMPTY || (r to c) in shipTiles)
            }) {
            // Clear old tiles
            for ((r, c) in shipTiles) {
                grid[r][c] = TileState.EMPTY
            }

            // Set new tiles
            for ((r, c) in newPositions) {
                grid[r][c] = TileState.SHIP(shipId)
            }

            // Update shipRegistry
            shipRegistry[shipId] = newPositions
            onComplete(null)
        } else {
            onComplete(source) // Invalid move
        }
    }
}




@Composable
fun GameGrid(
    gridSize: Int = 6,
    grid: List<MutableList<TileState>>,
    onTileClick: (row: Int, col: Int) -> Unit
) {
    Column {
        for (row in 0 until gridSize) {
            Row {
                for (col in 0 until gridSize) {
                    val tile = grid[row][col]
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(2.dp)
                            .background(
                                color = when (tile) {
                                    TileState.EMPTY -> Color.LightGray
                                    is TileState.SHIP -> Color.Blue
                                    TileState.HIT -> Color.Red
                                    TileState.MISS -> Color.DarkGray
                                },
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onTileClick(row, col) },
                        contentAlignment = Alignment.Center
                    ) {
//                        Text("") // optional marker
                        Text(
                            text = when (tile) {
                                is TileState.SHIP -> "S"
                                TileState.HIT -> "X"
                                TileState.MISS -> "O"
                                else -> ""
                            },
                            color = Color.White
                        )

                    }
                }
            }
        }
    }
}



fun placeRandomShips(grid: List<MutableList<TileState>>, count: Int = 5) {
    val gridSize = grid.size
    var shipsPlaced = 0
    val random = java.util.Random()

    while (shipsPlaced < count) {
        val row = random.nextInt(gridSize)
        val col = random.nextInt(gridSize)

        if (grid[row][col] == TileState.EMPTY) {
            val shipTiles = listOf(Pair(r1, c1), Pair(r2, c2)) // however your ship is placed
            currentShipId++
            for ((r, c) in shipTiles) {
                grid[r][c] = TileState.SHIP(currentShipId)
            }
            shipRegistry[currentShipId] = shipTiles
        }
    }
}



@Composable
fun SizeDropdownMenu(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(onClick = { expanded = true }) {
            Text(selectedOption)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}



@Composable
fun ShipPlacementScreen(
    navController: NavHostController,
    playerGrid: List<MutableList<TileState>>,
    onPlacementComplete: () -> Unit,
    playerName: String
) {
    val gridSize = 6
//    val playerGrid = remember {
//        List(gridSize) { mutableStateListOf(*Array(gridSize) { TileState.EMPTY }) }
//    }

    var shipPlacements by remember { mutableStateOf(0) }
    var isRandomPlacementDone by remember { mutableStateOf(false) }
    var selectedShipSize by remember { mutableStateOf(Pair(1, 1)) } // width x height
    var orientation by remember { mutableStateOf("Horizontal") }

    val isReady = shipPlacements == 5 || isRandomPlacementDone

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$playerName: Place Your Ships. (Max 5)",
            fontSize = 20.sp,
            modifier = Modifier.padding(8.dp)
        )


        Spacer(modifier = Modifier.height(16.dp))

        GameGrid(
            gridSize = gridSize,
            grid = playerGrid,
            onTileClick = { row, col ->
                if (shipPlacements < 5 && !isRandomPlacementDone) {
                    val (w, h) = if (orientation == "Horizontal") {
                        Pair(selectedShipSize.first, selectedShipSize.second)
                    } else {
                        Pair(selectedShipSize.second, selectedShipSize.first)
                    }

                    if (canPlaceShip(playerGrid, row, col, w, h)) {
                        placeShip(playerGrid, row, col, w, h)
                        shipPlacements++
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Ship Size Selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ship Size: ")
            DropdownMenuShipSize(selectedShipSize) { selectedShipSize = it }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = {
                orientation = if (orientation == "Horizontal") "Vertical" else "Horizontal"
            }) {
                Text("Orientation: $orientation")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row {
            Button(onClick = {
                randomlyPlaceShips(playerGrid)
                isRandomPlacementDone = true
                shipPlacements = 5
            }) {
                Text("Random Placement")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    onPlacementComplete()
                },
                enabled = isReady
            ) {
                Text("Ready")
            }
        }
    }
}

@Composable
fun DropdownMenuShipSize(selected: Pair<Int, Int>, onSelect: (Pair<Int, Int>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val shipSizes = listOf(
        Pair(1, 1) to "1x1",
        Pair(2, 1) to "2x1",
        Pair(1, 2) to "1x2"
    )

    Box {
        Button(onClick = { expanded = true }) {
            Text(shipSizes.first { it.first == selected }.second)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            shipSizes.forEach { (size, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(size)
                        expanded = false
                    }
                )
            }
        }
    }
}


fun canPlaceShip(grid: List<MutableList<TileState>>, row: Int, col: Int, width: Int, height: Int): Boolean {
    if (row + height > grid.size || col + width > grid[0].size) return false

    for (r in row until row + height) {
        for (c in col until col + width) {
            if (grid[r][c] != TileState.EMPTY) return false
        }
    }
    return true
}

fun placeShip(grid: List<MutableList<TileState>>, row: Int, col: Int, width: Int, height: Int) {
    for (r in row until row + height) {
        for (c in col until col + width) {
            grid[r][c] = TileState.SHIP
        }
    }
}

fun randomlyPlaceShips(grid: List<MutableList<TileState>>) {
    clearGrid(grid)  // Clear any previous placements

    val shipSizes = listOf(Pair(1, 1), Pair(2, 1), Pair(1, 2))
    val gridSize = grid.size
    var placedShips = 0
    val random = Random(System.currentTimeMillis())

    while (placedShips < 5) {
        val size = shipSizes.random()
        val isHorizontal = random.nextBoolean()
        val width = if (isHorizontal) size.first else size.second
        val height = if (isHorizontal) size.second else size.first

        val row = random.nextInt(gridSize - height + 1)
        val col = random.nextInt(gridSize - width + 1)

        if (canPlaceShip(grid, row, col, width, height)) {
            placeShip(grid, row, col, width, height)
            placedShips++
        }
    }
}


fun clearGrid(grid: List<MutableList<TileState>>) {
    for (row in grid) {
        for (i in row.indices) {
            row[i] = TileState.EMPTY
        }
    }
}

