<template>
  <div class="virtual-table-optimized" ref="container" @scroll="handleScroll">
    <!-- 表头区域 -->
    <div class="table-header" :style="headerStyle">
      <div class="header-row" :style="{ transform: `translateX(-${scrollLeft}px)` }">
        <!-- 固定列 -->
        <div class="fixed-columns">
          <div 
            v-for="col in fixedColumns" 
            :key="col.dataIndex"
            class="header-cell fixed-cell"
            :style="{ width: col.width + 'px' }"
          >
            {{ col.title }}
          </div>
        </div>
        <!-- 虚拟滚动列 -->
        <div class="virtual-columns" :style="{ transform: `translateX(${virtualColumnOffset}px)` }">
          <div 
            v-for="col in visibleColumns" 
            :key="col.dataIndex"
            class="header-cell virtual-cell"
            :style="{ width: col.width + 'px' }"
          >
            {{ col.title }}
          </div>
        </div>
      </div>
    </div>

    <!-- 数据区域 -->
    <div class="table-body" :style="{ height: containerHeight + 'px' }">
      <!-- 占位区域 -->
      <div :style="{ height: totalHeight + 'px', position: 'relative' }">
        <!-- 可见行区域 -->
        <div 
          class="visible-rows"
          :style="{ 
            transform: `translateY(${rowOffset}px)`,
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0
          }"
        >
          <div 
            v-for="(row, rowIndex) in visibleRows" 
            :key="getRowKey(row, rowIndex)"
            class="data-row"
            :style="{ height: rowHeight + 'px', transform: `translateX(-${scrollLeft}px)` }"
          >
            <!-- 固定列数据 -->
            <div class="fixed-columns">
              <div 
                v-for="col in fixedColumns" 
                :key="col.dataIndex"
                class="data-cell fixed-cell"
                :style="{ width: col.width + 'px' }"
                @click="handleCellClick(row, col, rowIndex)"
              >
                <span class="cell-content">{{ getCellValue(row, col) }}</span>
              </div>
            </div>
            <!-- 虚拟列数据 -->
            <div class="virtual-columns" :style="{ transform: `translateX(${virtualColumnOffset}px)` }">
              <div 
                v-for="col in visibleColumns" 
                :key="col.dataIndex"
                class="data-cell virtual-cell"
                :style="{ width: col.width + 'px' }"
                @click="handleCellClick(row, col, rowIndex)"
              >
                <span class="cell-content">{{ getCellValue(row, col) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 性能监控 (开发模式) -->
    <div v-if="isDevelopment && showDebugInfo" class="debug-info">
      <div>可见行: {{ visibleRows.length }} / {{ dataSource.length }}</div>
      <div>可见列: {{ visibleColumns.length }} / {{ virtualColumns.length }}</div>
      <div>渲染单元格: {{ visibleRows.length * visibleColumns.length }}</div>
      <div>滚动位置: {{ Math.round(scrollTop) }}, {{ Math.round(scrollLeft) }}</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'VirtualTableOptimized',
  props: {
    dataSource: {
      type: Array,
      default: () => []
    },
    columns: {
      type: Array,
      default: () => []
    },
    fixedColumnCount: {
      type: Number,
      default: 3 // 固定前几列
    },
    rowHeight: {
      type: Number,
      default: 54
    },
    containerHeight: {
      type: Number,
      default: 600
    },
    rowKey: {
      type: [String, Function],
      default: 'id'
    },
    overscanRows: {
      type: Number,
      default: 3 // 行缓冲区
    },
    overscanColumns: {
      type: Number,
      default: 2 // 列缓冲区
    },
    columnWidth: {
      type: Number,
      default: 120 // 默认列宽
    },
    showDebugInfo: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      scrollTop: 0,
      scrollLeft: 0,
      visibleRows: [],
      visibleColumns: [],
      rowOffset: 0,
      virtualColumnOffset: 0,
      isDevelopment: process.env.NODE_ENV === 'development',
      updateTimer: null
    }
  },
  computed: {
    // 总高度
    totalHeight() {
      return this.dataSource.length * this.rowHeight
    },

    // 可见行数
    visibleRowCount() {
      return Math.ceil(this.containerHeight / this.rowHeight)
    },

    // 可见列宽度
    visibleColumnWidth() {
      return this.$refs.container ? this.$refs.container.clientWidth : 800
    },

    // 固定列
    fixedColumns() {
      return this.columns.slice(0, this.fixedColumnCount).map(col => ({
        ...col,
        width: col.width || this.columnWidth
      }))
    },

    // 可虚拟化的列
    virtualColumns() {
      return this.columns.slice(this.fixedColumnCount).map(col => ({
        ...col,
        width: col.width || this.columnWidth
      }))
    },

    // 固定列总宽度
    fixedColumnsWidth() {
      return this.fixedColumns.reduce((width, col) => width + col.width, 0)
    },

    // 表头样式
    headerStyle() {
      return {
        height: this.rowHeight + 'px',
        position: 'sticky',
        top: 0,
        zIndex: 100,
        background: '#fafafa',
        borderBottom: '1px solid #f0f0f0'
      }
    }
  },
  watch: {
    dataSource: {
      handler() {
        this.$nextTick(() => {
          this.updateVisibleData()
        })
      },
      immediate: true
    },
    columns: {
      handler() {
        this.$nextTick(() => {
          this.updateVisibleData()
        })
      },
      immediate: true
    }
  },
  mounted() {
    this.updateVisibleData()
  },
  methods: {
    // 处理滚动事件（优化版）
    handleScroll(event) {
      const { scrollTop, scrollLeft } = event.target
      this.scrollTop = scrollTop
      this.scrollLeft = scrollLeft

      // 使用requestAnimationFrame优化滚动性能
      if (this.updateTimer) {
        cancelAnimationFrame(this.updateTimer)
      }

      this.updateTimer = requestAnimationFrame(() => {
        this.updateVisibleData()
      })
    },

    // 更新可见数据（核心优化方法）
    updateVisibleData() {
      this.updateVisibleRows()
      this.updateVisibleColumns()
    },

    // 更新可见行
    updateVisibleRows() {
      if (this.dataSource.length === 0) {
        this.visibleRows = []
        return
      }

      const startIndex = Math.floor(this.scrollTop / this.rowHeight)
      const endIndex = Math.min(
        startIndex + this.visibleRowCount + this.overscanRows * 2,
        this.dataSource.length
      )

      const actualStartIndex = Math.max(0, startIndex - this.overscanRows)
      const actualEndIndex = Math.min(this.dataSource.length, endIndex + this.overscanRows)

      this.visibleRows = this.dataSource.slice(actualStartIndex, actualEndIndex)
      this.rowOffset = actualStartIndex * this.rowHeight
    },

    // 更新可见列
    updateVisibleColumns() {
      if (this.virtualColumns.length === 0) {
        this.visibleColumns = []
        return
      }

      // 计算可见列范围
      const availableWidth = this.visibleColumnWidth - this.fixedColumnsWidth
      const scrollOffset = Math.max(0, this.scrollLeft - this.fixedColumnsWidth)
      
      let currentWidth = 0
      let startIndex = 0
      let endIndex = 0

      // 找到开始列
      for (let i = 0; i < this.virtualColumns.length; i++) {
        if (currentWidth + this.virtualColumns[i].width > scrollOffset) {
          startIndex = i
          break
        }
        currentWidth += this.virtualColumns[i].width
      }

      // 找到结束列
      currentWidth = 0
      for (let i = startIndex; i < this.virtualColumns.length; i++) {
        currentWidth += this.virtualColumns[i].width
        if (currentWidth >= availableWidth) {
          endIndex = i + 1
          break
        }
      }

      if (endIndex === 0) endIndex = this.virtualColumns.length

      // 添加缓冲区
      const actualStartIndex = Math.max(0, startIndex - this.overscanColumns)
      const actualEndIndex = Math.min(this.virtualColumns.length, endIndex + this.overscanColumns)

      this.visibleColumns = this.virtualColumns.slice(actualStartIndex, actualEndIndex)
      
      // 计算虚拟列偏移
      this.virtualColumnOffset = this.virtualColumns
        .slice(0, actualStartIndex)
        .reduce((offset, col) => offset + col.width, 0)
    },

    // 获取行键
    getRowKey(row, index) {
      if (typeof this.rowKey === 'function') {
        return this.rowKey(row, index)
      }
      return row[this.rowKey] || index
    },

    // 获取单元格值（优化版）
    getCellValue(row, col) {
      if (col.customRender) {
        return col.customRender(row[col.dataIndex], row)
      }
      
      const value = row[col.dataIndex]
      
      // 对数字进行格式化
      if (typeof value === 'number' && col.dataIndex.includes('out_')) {
        return value === 0 ? '-' : value.toFixed(2)
      }
      
      return value || '-'
    },

    // 处理单元格点击
    handleCellClick(row, col, rowIndex) {
      this.$emit('cell-click', {
        row,
        column: col,
        rowIndex,
        value: this.getCellValue(row, col)
      })
    },

    // 滚动到指定位置
    scrollTo(scrollTop = 0, scrollLeft = 0) {
      if (this.$refs.container) {
        this.$refs.container.scrollTop = scrollTop
        this.$refs.container.scrollLeft = scrollLeft
      }
    },

    // 滚动到指定行
    scrollToRow(rowIndex) {
      const scrollTop = rowIndex * this.rowHeight
      this.scrollTo(scrollTop, this.scrollLeft)
    },

    // 重置滚动位置
    resetScroll() {
      this.scrollTo(0, 0)
    }
  },

  beforeDestroy() {
    if (this.updateTimer) {
      cancelAnimationFrame(this.updateTimer)
    }
  }
}
</script>

<style scoped>
.virtual-table-optimized {
  position: relative;
  overflow: auto;
  border: 1px solid #f0f0f0;
  background: white;
}

.table-header {
  border-bottom: 2px solid #f0f0f0;
}

.header-row, .data-row {
  display: flex;
  align-items: center;
  min-height: 54px;
}

.fixed-columns {
  display: flex;
  position: sticky;
  left: 0;
  z-index: 10;
  background: inherit;
  border-right: 2px solid #f0f0f0;
}

.virtual-columns {
  display: flex;
}

.header-cell, .data-cell {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-right: 1px solid #f0f0f0;
  font-size: 14px;
  overflow: hidden;
  box-sizing: border-box;
}

.header-cell {
  font-weight: 600;
  background: #fafafa;
  color: #262626;
}

.data-cell {
  background: white;
  color: #595959;
  cursor: pointer;
  transition: background-color 0.2s;
}

.data-cell:hover {
  background: #f5f5f5;
}

.fixed-cell {
  background: #fafafa;
}

.data-row .fixed-cell {
  background: white;
}

.data-row:hover .fixed-cell {
  background: #f5f5f5;
}

.cell-content {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
}

.table-body {
  overflow: hidden;
}

.debug-info {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(0, 0, 0, 0.8);
  color: white;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  z-index: 200;
  line-height: 1.4;
}

/* 滚动条优化 */
.virtual-table-optimized::-webkit-scrollbar {
  width: 12px;
  height: 12px;
}

.virtual-table-optimized::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 6px;
}

.virtual-table-optimized::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 6px;
}

.virtual-table-optimized::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.virtual-table-optimized::-webkit-scrollbar-corner {
  background: #f1f1f1;
}
</style> 