import { FormTypes, getListData } from '@/utils/JEditableTableUtil'
import {
  findBySelectCus, findBySelectRetail, findBySelectSup, findStockByDepotAndBarCode, getAccount,
  getBatchNumberList, getCurrentSystemConfig, getMaterialByBarCode, getPersonByNumType, getPlatformConfigByKey
} from '@/api/api'
import { getAction } from '@/api/manage'
import { getCheckFlag, getMpListShort, getNowFormatDateTime } from '@/utils/util'
import { USER_INFO } from '@/store/mutation-types'
import Vue from 'vue'

const ALLOWED_VISIBLE_COLUMN_KEYS = new Set([
  'depotId',
  'barCode',
  'name',
  'stock',
  'operNumber',
  'unitPrice',
  'allPrice'
])

const FORCE_HIDE_COLUMNS_BY_PREFIX = {
  // 采购入库：隐藏仓库列；金额列保持隐藏
  CGRK: new Set(['allPrice', 'depotId']),
  // 销售出库/其它入库/其它出库：隐藏单价金额列，同时隐藏名称与仓库列
  XSCK: new Set(['unitPrice', 'allPrice', 'name', 'depotId']),
  QTRK: new Set(['unitPrice', 'allPrice', 'name', 'depotId']),
  QTCK: new Set(['unitPrice', 'allPrice', 'name', 'depotId'])
}

const FORCE_SHOW_COLUMNS_BY_PREFIX = {
  CGRK: new Set(['unitPrice'])
}

const AMOUNT_DISABLED_PREFIXES = new Set(['CGRK', 'XSCK', 'QTRK', 'QTCK'])

export const BillModalMixin = {
  data() {
    return {
      action: '',
      manyAccountBtnStatus: false,
      // 程序性新增/懒加载新增阶段的滚动抑制
      initialAddInProgress: false,
      lazyLoadingInProgress: false,
      supList: [],
      cusList: [],
      retailList: [],
      personList: {
        options: [],
        value: ''
      },
      currentSelectDepotId: '',
      transferParam: {},
      defaultDepotId: '',
      depotList: [],
      accountList: [],
      accountIdList: [],
      accountMoneyList: [],
      billUnitPirce: '',
      scanBarCode: '',
      scanStatus: true,
      billStatus: '0',
      minWidth: 1100,
      isCanCheck: true,
      quickBtn: {
        vendor: false,
        customer: false,
        member: false,
        account: false,
        depot: false
      },
      billPrintFlag: false,
      /* 是否显示打印按钮 */
      isShowPrintBtn: true,
      /* 原始审核是否开启 */
      checkFlag: true,
      //零收付款的场景开关
      zeroChangeAmountFlag: false,
      validatorRules: {
        price: {
          rules: [
            { pattern: /^(([0-9][0-9]*)|([0]\.\d{0,4}|[0-9][0-9]*\.\d{0,4}))$/, message: '金额格式不正确!' }
          ]
        }
      },
      spans: {
        labelCol1: { span: 2 },
        wrapperCol1: { span: 22 },
        //1_5: 分为1.5列（相当于占了2/3）
        labelCol1_5: { span: 3 },
        wrapperCol1_5: { span: 21 },
        labelCol2: { span: 4 },
        wrapperCol2: { span: 20 },
        labelCol3: { span: 6 },
        wrapperCol3: { span: 18 },
        labelCol6: { span: 12 },
        wrapperCol6: { span: 12 }
      },
    };
  },
  created() {
    let realScreenWidth = window.screen.width
    this.width = realScreenWidth < 1500 ? '1200px' : '1550px'
    this.minWidth = realScreenWidth < 1500 ? 1150 : 1500
  },
  mounted() {
    document.getElementById(this.prefixNo).addEventListener('keydown', this.handleOkKey)
  },
  beforeDestroy() {
    document.getElementById(this.prefixNo).removeEventListener('keydown', this.handleOkKey)
  },
  computed: {
    readOnly: function () {
      return this.action !== "add" && this.action !== "edit";
    }
  },
  methods: {
    // 快捷键
    handleOkKey(e) {
      const key = window.event.keyCode ? window.event.keyCode : window.event.which
      if (key === 83 && e.ctrlKey) {
        //保存 CTRL+S
        this.handleOk()
        e.preventDefault()
      }
    },
    addInit(amountNum) {
      getAction('/sequence/buildNumber').then((res) => {
        if (res && res.code === 200) {
          this.model.defaultNumber = amountNum + res.data.defaultNumber
          this.form.setFieldsValue({ 'number': amountNum + res.data.defaultNumber })
        }
      })
      this.$nextTick(() => {
        this.form.setFieldsValue({
          'operTime': getNowFormatDateTime(), 'discount': 0,
          'discountMoney': 0, 'discountLastMoney': 0, 'otherMoney': 0, 'changeAmount': 0, 'debt': 0
        })
      })
      this.$nextTick(() => {
        getAccount({}).then((res) => {
          if (res && res.code === 200) {
            for (const item of res.data.accountList) {
              if (item.isDefault) {
                this.form.setFieldsValue({ 'accountId': item.id })
              }
            }
          }
        })
      })
      this.accountIdList = []
      this.accountMoneyList = []
      this.manyAccountBtnStatus = false
    },
    copyAddInit(amountNum) {
      getAction('/sequence/buildNumber').then((res) => {
        if (res && res.code === 200) {
          this.form.setFieldsValue({ 'number': amountNum + res.data.defaultNumber })
        }
      })
      this.$nextTick(() => {
        this.form.setFieldsValue({ 'operTime': getNowFormatDateTime() })
      })
    },
    /** 查询某个tab的数据 */
    requestSubTableData(url, params, tab, success) {
      tab.loading = true
      getAction(url, params).then(res => {
        if (res && res.code === 200) {
          tab.dataSource = res.data.rows
          for (let i = 0; i < tab.dataSource.length; i++) {
            let info = tab.dataSource[i]
            info.isEdit = this.model.id ? 1 : 0
            this.changeColumnShow(info)
          }
          typeof success === 'function' ? success(res) : ''
        }
      }).finally(() => {
        tab.loading = false
      })
    },
    //改变字段的状态，1-显示 0-隐藏
    changeFormTypes(columns, key, type) {
      for (let i = 0; i < columns.length; i++) {
        if (columns[i].key === key) {
          if (type) {
            if (key === 'snList' || key === 'batchNumber') {
              if (this.prefixNo === 'LSCK' || this.prefixNo === 'CGTH' || this.prefixNo === 'XSCK' || this.prefixNo === 'QTCK' || this.prefixNo === 'DBCK') {
                columns[i].type = FormTypes.popupJsh //显示
              } else {
                if (key === 'snList') {
                  columns[i].type = FormTypes.popupJsh //显示
                } else {
                  columns[i].type = FormTypes.input //显示
                }
              }
            } else if (key === 'expirationDate') {
              if (this.prefixNo === 'LSTH' || this.prefixNo === 'CGRK' || this.prefixNo === 'XSTH' || this.prefixNo === 'QTRK') {
                columns[i].type = FormTypes.date //显示
              } else {
                columns[i].type = FormTypes.input //显示
              }
            } else {
              columns[i].type = FormTypes.normal //显示
            }
          } else {
            columns[i].type = FormTypes.hidden //隐藏
          }
        }
      }
    },
    initSystemConfig() {
      getCurrentSystemConfig().then((res) => {
        if (res.code === 200 && res.data) {
          let multiBillType = res.data.multiBillType
          let multiLevelApprovalFlag = res.data.multiLevelApprovalFlag
          this.checkFlag = getCheckFlag(multiBillType, multiLevelApprovalFlag, this.prefixNo)
          this.purchaseBySaleFlag = res.data.purchaseBySaleFlag === '1' ? true : false
          this.inOutManageFlag = res.data.inOutManageFlag === '1' ? true : false
          this.zeroChangeAmountFlag = res.data.zeroChangeAmountFlag === '1' ? true : false
          if (res.data.auditPrintFlag === '1') {
            if (this.model.status === '0' || this.model.status === '9') {
              this.isShowPrintBtn = false
            } else {
              this.isShowPrintBtn = true
            }
          } else {
            this.isShowPrintBtn = true
          }
        }
      })
    },
    initSupplier(isChecked) {
      let that = this;
      findBySelectSup({}).then((res) => {
        if (res) {
          that.supList = res
          if (isChecked && res.length > 0) {
            that.form.setFieldsValue({ 'organId': res[0].id })
          }
        }
      });
    },
    initCustomer(isChecked) {
      let that = this;
      findBySelectCus({}).then((res) => {
        if (res) {
          that.cusList = res
          if (isChecked && res.length > 0) {
            that.form.setFieldsValue({ 'organId': res[0].id })
          }
        }
      });
    },
    initRetail(isChecked) {
      let that = this;
      findBySelectRetail({}).then((res) => {
        if (res) {
          that.retailList = res
          if (isChecked && res.length > 0) {
            that.form.setFieldsValue({ 'organId': res[0].id })
          }
        }
      });
    },
    initSalesman() {
      let that = this;
      getPersonByNumType({ type: 1 }).then((res) => {
        if (res) {
          that.personList.options = res;
        }
      });
    },
    initDepot() {
      let that = this;
      getAction('/depot/findDepotByCurrentUser').then((res) => {
        if (res.code === 200) {
          let arr = res.data
          for (let item of that.materialTable.columns) {
            if (item.key == 'depotId' || item.key == 'anotherDepotId') {
              item.options = []
              for (let i = 0; i < arr.length; i++) {
                let depotInfo = {};
                depotInfo.value = arr[i].id + '' //注意-此处value必须为字符串格式
                depotInfo.text = arr[i].depotName
                depotInfo.title = arr[i].depotName
                item.options.push(depotInfo)
              }
            }
          }
        }
      })
    },
    initAccount(isChecked) {
      let that = this;
      getAccount({}).then((res) => {
        if (res && res.code === 200) {
          let list = res.data.accountList
          let lastId = list.length > 0 ? list[0].id : ''
          getCurrentSystemConfig().then((res) => {
            if (res.code === 200 && res.data) {
              let multiAccountFlag = res.data.multiAccountFlag
              if (multiAccountFlag === '1') {
                list.splice(0, 0, { id: 0, name: '多账户' })
              }
            }
            that.accountList = list
            if (isChecked) {
              that.form.setFieldsValue({ 'accountId': lastId })
            }
          })
        }
      })
    },
    handleManyAccount() {
      this.selectAccount(0)
    },
    selectAccount(value) {
      if (value === 0) { //多账户
        this.$refs.manyAccountModalForm.edit(this.accountIdList, this.accountMoneyList)
        this.$refs.manyAccountModalForm.title = "多账户结算"
        this.manyAccountBtnStatus = true
      } else {
        this.accountIdList = []
        this.accountMoneyList = []
        this.manyAccountBtnStatus = false
      }
    },
    manyAccountModalFormOk(idList, moneyList, allPrice) {
      this.accountIdList = idList
      this.accountMoneyList = moneyList
      let discountLastMoney = parseFloat(this.form.getFieldValue('discountLastMoney')) || 0
      let otherMoney = this.form.getFieldValue('otherMoney') ? parseFloat(this.form.getFieldValue('otherMoney')) || 0 : 0
      let debt = parseFloat((discountLastMoney + otherMoney - (parseFloat(allPrice) || 0)).toFixed(2)) || 0
      this.$nextTick(() => {
        this.form.setFieldsValue({ 'changeAmount': parseFloat(allPrice) || 0, 'debt': debt })
      });
    },
    addSupplier() {
      this.$refs.vendorModalForm.add();
      this.$refs.vendorModalForm.title = "新增供应商";
      this.$refs.vendorModalForm.disableSubmit = false;
    },
    addCustomer() {
      this.$refs.customerModalForm.add();
      this.$refs.customerModalForm.title = "新增客户（提醒：如果找不到新添加的客户，请到用户管理检查是否分配了该客户权限）";
      this.$refs.customerModalForm.disableSubmit = false;
    },
    addMember() {
      this.$refs.memberModalForm.add();
      this.$refs.memberModalForm.title = "新增会员";
      this.$refs.memberModalForm.disableSubmit = false;
    },
    handleBatchSetDepot() {
      this.$refs.batchSetDepotModalForm.add();
      this.$refs.batchSetDepotModalForm.title = "批量切换仓库";
      this.$refs.batchSetDepotModalForm.disableSubmit = false;
    },
    addDepot() {
      this.$refs.depotModalForm.add();
      this.$refs.depotModalForm.title = "新增仓库";
      this.$refs.depotModalForm.disableSubmit = false;
    },
    addAccount() {
      this.$refs.accountModalForm.add();
      this.$refs.accountModalForm.title = "新增结算账户";
      this.$refs.accountModalForm.disableSubmit = false;
    },
    vendorModalFormOk() {
      this.initSupplier(1)
    },
    customerModalFormOk() {
      this.initCustomer(1)
    },
    memberModalFormOk() {
      this.initRetail(1)
    },
    batchSetDepotModalFormOk(depotId) {
      this.getAllTable().then(tables => {
        return getListData(this.form, tables)
      }).then(allValues => {
        //获取单据明细列表信息
        let detailArr = allValues.tablesValue[0].values
        let barCodes = ''
        for (let detail of detailArr) {
          barCodes += detail.barCode + ','
        }
        if (barCodes) {
          barCodes = barCodes.substring(0, barCodes.length - 1)
        }
        let param = {
          barCode: barCodes,
          organId: this.form.getFieldValue('organId'),
          depotId: depotId,
          mpList: getMpListShort(Vue.ls.get('materialPropertyList')),  //扩展属性
          prefixNo: this.prefixNo
        }
        getMaterialByBarCode(param).then((res) => {
          if (res && res.code === 200) {
            let mList = res.data
            //构造新的列表数组，用于存放单据明细信息
            let newDetailArr = []
            if (mList && mList.length) {
              for (let i = 0; i < detailArr.length; i++) {
                let item = detailArr[i]
                item.depotId = depotId
                for (let j = 0; j < mList.length; j++) {
                  if (mList[j].mBarCode === item.barCode) {
                    item.stock = mList[j].stock
                  }
                }
                newDetailArr.push(item)
              }
            } else {
              for (let i = 0; i < detailArr.length; i++) {
                let item = detailArr[i]
                item.depotId = depotId
                newDetailArr.push(item)
              }
            }
            this.materialTable.dataSource = newDetailArr
          }
        })
      })
    },
    depotModalFormOk() {
      this.initDepot()
    },
    accountModalFormOk() {
      this.initAccount(1)
    },
    workflowModalFormOk() {
      this.close()
    },
    onAdded(event) {
      const { row, target } = event
      // 初始化批量新增或懒加载新增时，不进行自动滚动，避免闪烁
      if (this.initialAddInProgress || this.lazyLoadingInProgress) {
        return
      }
      // 仅在用户触发的新增行时，滚动到底部以聚焦新增行
      setTimeout(() => {
        if (this.$refs.materialDataTable) {
          this.$refs.materialDataTable.resetScrollTop((target.rows.length + 1) * this.$refs.materialDataTable.rowHeight)
        }
      }, 300)
    },
    //单元值改变一个字符就触发一次
    onValueChange(event) {
      let that = this
      const { type, row, column, value, target } = event
      let param, snList, batchNumber, operNumber, unitPrice, allPrice, taxRate, taxMoney, taxLastMoney
      const amountDisabled = this.amountCalculationDisabled()
      switch (column.key) {
        case "depotId":
          that.currentSelectDepotId = row.depotId
          if (row.barCode) {
            that.getStockByDepotBarCode(row, target)
          }
          break;
        case "barCode":
          // XSCK/QTRK/QTCK：单唛头时仅查询库存，不请求商品详情，不回填金额/单价/名称等
          if (typeof value === 'string' && value.indexOf(',') === -1
            && (this.prefixNo === 'XSCK' || this.prefixNo === 'QTRK' || this.prefixNo === 'QTCK')) {
            const depotIdSelected = this.prefixNo !== 'CGDD' && this.prefixNo !== 'XSDD' ? row.depotId : ''
            findStockByDepotAndBarCode({ depotId: depotIdSelected, barCode: value }).then((res) => {
              if (res && res.code === 200) {
                target.setValues([{ rowKey: row.id, values: { stock: res.data.stock } }])
                target.$forceUpdate()
              }
            })
            break;
          }
          const prevOperNumber = row.operNumber
          param = {
            barCode: value,
            organId: this.form.getFieldValue('organId'),
            mpList: getMpListShort(Vue.ls.get('materialPropertyList')),  //扩展属性
            prefixNo: this.prefixNo
          }
          getMaterialByBarCode(param).then((res) => {
            if (res && res.code === 200) {
              let mList = res.data
              if (value.indexOf(',') > -1) {
                //多个唛头
                this.$refs.materialDataTable.getValues((error, values) => {
                  values.pop()  //移除最后一行数据
                  let mArr = values
                  for (let i = 0; i < mList.length; i++) {
                    let mInfo = mList[i]
                    this.changeColumnShow(mInfo)
                    let mObj = this.parseInfoToObj(mInfo)
                    mObj.depotId = mInfo.depotId
                    mObj.stock = mInfo.stock
                    mArr.push(mObj)
                  }
                  let allPriceTotal = 0
                  let taxLastMoneyTotal = 0
                  for (let j = 0; j < mArr.length; j++) {
                    allPriceTotal += mArr[j].allPrice - 0
                    taxLastMoneyTotal += mArr[j].taxLastMoney - 0
                    //组合和拆分单据给商品类型进行重新赋值
                    if (j === 0) {
                      mArr[0].mType = '组合件'
                    } else {
                      mArr[j].mType = '普通子件'
                    }
                  }
                  this.materialTable.dataSource = mArr
                  this.normalizeAmountsForDisabled()
                  if (!amountDisabled) {
                    if (this.prefixNo === 'LSCK' || this.prefixNo === 'LSTH') {
                      target.statisticsColumns.allPrice = allPriceTotal
                    } else {
                      target.statisticsColumns.taxLastMoney = taxLastMoneyTotal
                    }
                    that.autoChangePrice(target)
                  }
                  //强制渲染
                  target.$forceUpdate()
                })
              } else {
                //单个唛头
                let depotIdSelected = this.prefixNo !== 'CGDD' && this.prefixNo !== 'XSDD' ? row.depotId : ''
                findStockByDepotAndBarCode({ depotId: depotIdSelected, barCode: row.barCode }).then((res) => {
                  if (res && res.code === 200) {
                    let mArr = []
                    let mInfo = mList[0]
                    this.changeColumnShow(mInfo)
                    let mInfoEx = this.parseInfoToObj(mInfo)
                    mInfoEx.stock = res.data.stock
                    let mObj = {
                      rowKey: row.id,
                      values: mInfoEx
                    }
                    mArr.push(mObj)
                    target.setValues(mArr);
                    if (amountDisabled) {
                      target.setValues([{
                        rowKey: row.id,
                        values: { allPrice: 0, taxMoney: 0, taxLastMoney: 0 }
                      }])
                    }
                    if (!amountDisabled && prevOperNumber != null && prevOperNumber !== '') {
                      let restoredOperNumber = Number(prevOperNumber)
                      if (!isNaN(restoredOperNumber)) {
                        const unitPriceNew = Number(mInfoEx.unitPrice) || 0
                        const taxRateNew = Number(mInfoEx.taxRate) || 0
                        const allPriceNew = Number((unitPriceNew * restoredOperNumber).toFixed(2))
                        const taxMoneyNew = Number(((taxRateNew * 0.01) * allPriceNew).toFixed(2))
                        const taxLastMoneyNew = Number((allPriceNew + taxMoneyNew).toFixed(2))
                        target.setValues([
                          {
                            rowKey: row.id,
                            values: {
                              operNumber: restoredOperNumber,
                              allPrice: allPriceNew,
                              taxMoney: taxMoneyNew,
                              taxLastMoney: taxLastMoneyNew
                            }
                          }
                        ])
                      }
                    }
                    if (!amountDisabled) {
                      target.recalcAllStatisticsColumns()
                      that.autoChangePrice(target)
                    }
                    this.normalizeAmountsForDisabled()
                    target.autoSelectBySpecialKey('operNumber', row.orderNum)
                    //强制渲染
                    target.$forceUpdate()
                  }
                })
              }
            }
          });
          break;
        case "snList":
          if (amountDisabled) {
            break;
          }
          snList = value
          if (snList) {
            snList = snList.replaceAll('，', ',')
            let snArr = snList.split(',')
            operNumber = snArr.length
            taxRate = row.taxRate - 0 //税率
            unitPrice = row.unitPrice - 0 //单价
            allPrice = (unitPrice * operNumber).toFixed(2) - 0
            taxMoney = ((taxRate * 0.01) * allPrice).toFixed(2) - 0
            taxLastMoney = (allPrice + taxMoney).toFixed(2) - 0
            target.setValues([{ rowKey: row.id, values: { operNumber: operNumber, allPrice: allPrice, taxMoney: taxMoney, taxLastMoney: taxLastMoney } }])
            target.recalcAllStatisticsColumns()
            that.autoChangePrice(target)
          }
          break;
        case "batchNumber":
          if (amountDisabled) {
            break;
          }
          //只针对销售出库、采购退货、其它出库
          if (this.prefixNo === 'XSCK' || this.prefixNo === 'CGTH' || this.prefixNo === 'QTCK') {
            batchNumber = value
            let depotItemId = ''
            if (this.model.id) {
              //只有在保存之后的编辑页面下才获取明细id
              let rowId = row.id
              if (rowId.length <= 19) {
                depotItemId = rowId - 0
              }
            }
            getBatchNumberList({ name: '', depotItemId: depotItemId, depotId: row.depotId, barCode: row.barCode, batchNumber: batchNumber }).then((res) => {
              if (res && res.code === 200) {
                if (res.data && res.data.rows) {
                  let info = res.data.rows[0]
                  let preNumber = row.preNumber - 0 //原数量
                  let finishNumber = row.finishNumber - 0 //已出库
                  let totalNum = info.totalNum - 0 //批次数量
                  if (preNumber > 0) {
                    if (totalNum > preNumber - finishNumber) {
                      operNumber = preNumber - finishNumber
                    } else {
                      operNumber = totalNum
                    }
                  } else {
                    operNumber = totalNum
                  }
                  taxRate = row.taxRate - 0 //税率
                  unitPrice = row.unitPrice - 0 //单价
                  allPrice = (unitPrice * operNumber).toFixed(2) - 0
                  taxMoney = ((taxRate * 0.01) * allPrice).toFixed(2) - 0
                  taxLastMoney = (allPrice + taxMoney).toFixed(2) - 0
                  target.setValues([{
                    rowKey: row.id, values: {
                      expirationDate: info.expirationDateStr, operNumber: operNumber,
                      allPrice: allPrice, taxMoney: taxMoney, taxLastMoney: taxLastMoney
                    }
                  }])
                  target.recalcAllStatisticsColumns()
                  that.autoChangePrice(target)
                }
              }
            })
          }
          break;
        case "operNumber":
          if (amountDisabled) {
            break;
          }
          operNumber = value - 0
          taxRate = row.taxRate - 0 //税率
          unitPrice = row.unitPrice - 0 //单价
          allPrice = (unitPrice * operNumber).toFixed(2) - 0
          taxMoney = ((taxRate * 0.01) * allPrice).toFixed(2) - 0
          taxLastMoney = (allPrice + taxMoney).toFixed(2) - 0
          target.setValues([{ rowKey: row.id, values: { allPrice: allPrice, taxMoney: taxMoney, taxLastMoney: taxLastMoney } }])
          target.recalcAllStatisticsColumns()
          that.autoChangePrice(target)
          break;
        case "unitPrice":
          if (amountDisabled) {
            break;
          }
          operNumber = row.operNumber - 0 //数量
          unitPrice = value - 0 //单价
          taxRate = row.taxRate - 0 //税率
          allPrice = (unitPrice * operNumber).toFixed(2) - 0
          taxMoney = ((taxRate * 0.01) * allPrice).toFixed(2) - 0
          taxLastMoney = (allPrice + taxMoney).toFixed(2) - 0
          target.setValues([{ rowKey: row.id, values: { allPrice: allPrice, taxMoney: taxMoney, taxLastMoney: taxLastMoney } }])
          target.recalcAllStatisticsColumns()
          that.autoChangePrice(target)
          break;
        case "allPrice":
          if (amountDisabled) {
            break;
          }
          operNumber = row.operNumber - 0 //数量
          taxRate = row.taxRate - 0 //税率
          allPrice = value - 0
          unitPrice = (allPrice / operNumber).toFixed(4) - 0 //单价
          taxMoney = ((taxRate * 0.01) * allPrice).toFixed(2) - 0
          taxLastMoney = (allPrice + taxMoney).toFixed(2) - 0
          target.setValues([{ rowKey: row.id, values: { unitPrice: unitPrice, taxMoney: taxMoney, taxLastMoney: taxLastMoney } }])
          target.recalcAllStatisticsColumns()
          that.autoChangePrice(target)
          break;
        case "taxRate":
          if (amountDisabled) {
            break;
          }
          operNumber = row.operNumber - 0 //数量
          allPrice = row.allPrice - 0
          unitPrice = row.unitPrice - 0
          taxRate = value - 0 //税率
          taxMoney = ((taxRate * 0.01) * allPrice).toFixed(2) - 0
          taxLastMoney = (allPrice + taxMoney).toFixed(2) - 0
          target.setValues([{ rowKey: row.id, values: { taxMoney: taxMoney, taxLastMoney: taxLastMoney } }])
          target.recalcAllStatisticsColumns()
          that.autoChangePrice(target)
          break;
        case "taxLastMoney":
          if (amountDisabled) {
            break;
          }
          operNumber = row.operNumber - 0 //数量
          taxLastMoney = value - 0
          taxRate = row.taxRate - 0 //税率
          if (taxRate) {
            unitPrice = (taxLastMoney / operNumber / (1 + taxRate * 0.01)).toFixed(4) - 0
            allPrice = (unitPrice * operNumber).toFixed(2) - 0
            taxMoney = (taxLastMoney - allPrice).toFixed(2) - 0
          } else {
            //税率为0的情况，特殊处理
            allPrice = taxLastMoney
            unitPrice = (allPrice / operNumber).toFixed(4) - 0 //单价
            taxMoney = 0
          }
          target.setValues([{ rowKey: row.id, values: { unitPrice: unitPrice, allPrice: allPrice, taxMoney: taxMoney } }])
          target.recalcAllStatisticsColumns()
          that.autoChangePrice(target)
          break;
      }
    },
    //转为商品对象
    parseInfoToObj(mInfo) {
      const amountDisabled = this.amountCalculationDisabled()
      return {
        barCode: mInfo.mBarCode,
        name: mInfo.name,
        standard: mInfo.standard,
        model: mInfo.model,
        color: mInfo.color,
        brand: mInfo.brand,
        mfrs: mInfo.mfrs,
        otherField1: mInfo.otherField1,
        otherField2: mInfo.otherField2,
        otherField3: mInfo.otherField3,
        unit: mInfo.commodityUnit,
        sku: mInfo.sku,
        unitPrice: mInfo.billPrice,
        allPrice: amountDisabled ? 0 : mInfo.billPrice,
        taxRate: 0,
        taxMoney: 0,
        taxLastMoney: amountDisabled ? 0 : mInfo.billPrice
      }
    },
    //统一保留允许的列，隐藏其余列
    changeColumnHide() {
      if (!this.materialTable || !(this.materialTable.columns instanceof Array)) {
        return
      }
      this.materialTable.columns.forEach(column => {
        if (!column || !column.key) {
          return
        }
        if (column.__originType == null) {
          column.__originType = column.type
        }
        const forceHideSet = FORCE_HIDE_COLUMNS_BY_PREFIX[this.prefixNo]
        if (forceHideSet && forceHideSet.has(column.key)) {
          this.changeFormTypes(this.materialTable.columns, column.key, 0)
          return
        }
        const forceShowSet = FORCE_SHOW_COLUMNS_BY_PREFIX[this.prefixNo]
        if (forceShowSet && forceShowSet.has(column.key)) {
          column.type = column.__originType
          return
        }
        if (ALLOWED_VISIBLE_COLUMN_KEYS.has(column.key)) {
          column.type = column.__originType
        } else {
          this.changeFormTypes(this.materialTable.columns, column.key, 0)
        }
      })
    },
    //根据数据需求动态显示列（受允许列限制）
    changeColumnShow(info) {
      if (!info) {
        return
      }
      const ensureVisible = (key, handler) => {
        if (!ALLOWED_VISIBLE_COLUMN_KEYS.has(key)) {
          return
        }
        handler()
      }

      if (info.model) {
        ensureVisible('model', () => this.changeFormTypes(this.materialTable.columns, 'model', 1))
      }
      if (info.color) {
        ensureVisible('color', () => this.changeFormTypes(this.materialTable.columns, 'color', 1))
      }
      if (info.brand) {
        ensureVisible('brand', () => this.changeFormTypes(this.materialTable.columns, 'brand', 1))
      }
      if (info.mfrs) {
        ensureVisible('mfrs', () => this.changeFormTypes(this.materialTable.columns, 'mfrs', 1))
      }
      if (info.otherField1) {
        ensureVisible('otherField1', () => this.changeFormTypes(this.materialTable.columns, 'otherField1', 1))
      }
      if (info.otherField2) {
        ensureVisible('otherField2', () => this.changeFormTypes(this.materialTable.columns, 'otherField2', 1))
      }
      if (info.otherField3) {
        ensureVisible('otherField3', () => this.changeFormTypes(this.materialTable.columns, 'otherField3', 1))
      }
      if (info.sku) {
        ensureVisible('sku', () => this.changeFormTypes(this.materialTable.columns, 'sku', 1))
      }
      if (info.enableSerialNumber === "1") {
        ensureVisible('snList', () => {
          if (this.inOutManageFlag && (this.prefixNo === 'CGRK' || this.prefixNo === 'CGTH' || this.prefixNo === 'XSCK' || this.prefixNo === 'XSTH')) {
            return
          }
          this.changeFormTypes(this.materialTable.columns, 'snList', 1)
        })
      }
      if (info.enableBatchNumber === "1") {
        ensureVisible('batchNumber', () => {
          if (this.inOutManageFlag && (this.prefixNo === 'CGRK' || this.prefixNo === 'CGTH' || this.prefixNo === 'XSCK' || this.prefixNo === 'XSTH')) {
            return
          }
          this.changeFormTypes(this.materialTable.columns, 'batchNumber', 1)
          ensureVisible('expirationDate', () => this.changeFormTypes(this.materialTable.columns, 'expirationDate', 1))
        })
      }
    },
    amountCalculationDisabled() {
      return AMOUNT_DISABLED_PREFIXES.has(this.prefixNo)
    },
    normalizeAmountsForDisabled() {
      if (!this.amountCalculationDisabled()) {
        return
      }
      if (!this.materialTable || !(this.materialTable.dataSource instanceof Array)) {
        return
      }
      this.materialTable.dataSource.forEach(item => {
        if (item) {
          item.allPrice = 0
          item.taxMoney = 0
          item.taxLastMoney = 0
        }
      })
    },
    //删除一行或多行的时候触发
    onDeleted(ids, target) {
      target.recalcAllStatisticsColumns()
      this.autoChangePrice(target)
    },
    //根据仓库和唛头查询库存
    getStockByDepotBarCode(row, target) {
      findStockByDepotAndBarCode({ depotId: row.depotId, barCode: row.barCode }).then((res) => {
        if (res && res.code === 200) {
          target.setValues([{ rowKey: row.id, values: { stock: res.data.stock } }])
          target.recalcAllStatisticsColumns()
        }
      })
    },
    //改变优惠、本次付款、欠款的值
    autoChangePrice(target) {
      if (this.amountCalculationDisabled()) {
        return
      }
      let allTaxLastMoney = target && target.statisticsColumns && target.statisticsColumns.taxLastMoney ?
        parseFloat(target.statisticsColumns.taxLastMoney) || 0 : 0
      let discount = parseFloat(this.form.getFieldValue('discount')) || 0
      let otherMoney = this.form.getFieldValue('otherMoney') ? parseFloat(this.form.getFieldValue('otherMoney')) || 0 : 0
      let deposit = this.form.getFieldValue('deposit') ? parseFloat(this.form.getFieldValue('deposit')) || 0 : 0
      let discountMoney = parseFloat((discount * 0.01 * allTaxLastMoney).toFixed(2)) || 0
      let discountLastMoney = parseFloat((allTaxLastMoney - discountMoney).toFixed(2)) || 0
      let changeAmountNew = parseFloat((discountLastMoney + otherMoney).toFixed(2)) || 0
      if (deposit) {
        changeAmountNew = parseFloat((changeAmountNew - deposit).toFixed(2)) || 0
      }
      this.$nextTick(() => {
        changeAmountNew = this.prefixNo === 'CGDD' || this.prefixNo === 'XSDD' ? 0 : changeAmountNew
        this.form.setFieldsValue({
          'discount': discount, 'discountMoney': discountMoney, 'discountLastMoney': discountLastMoney,
          'changeAmount': changeAmountNew, 'debt': 0
        })
        this.setZeroChangeAmount()
      });
    },
    //改变优惠率
    onChangeDiscount(e) {
      if (this.amountCalculationDisabled()) {
        return
      }
      const value = parseFloat(e.target.value) || 0
      let otherMoney = this.form.getFieldValue('otherMoney') ? parseFloat(this.form.getFieldValue('otherMoney')) || 0 : 0
      let deposit = this.form.getFieldValue('deposit') ? parseFloat(this.form.getFieldValue('deposit')) || 0 : 0
      let allTaxLastMoney = this.$refs.materialDataTable && this.$refs.materialDataTable.statisticsColumns &&
        this.$refs.materialDataTable.statisticsColumns.taxLastMoney ?
        parseFloat(this.$refs.materialDataTable.statisticsColumns.taxLastMoney) || 0 : 0
      let discountMoneyNew = parseFloat((allTaxLastMoney * value * 0.01).toFixed(2)) || 0
      let discountLastMoneyNew = parseFloat((allTaxLastMoney - discountMoneyNew).toFixed(2)) || 0
      let changeAmountNew = parseFloat((discountLastMoneyNew + otherMoney).toFixed(2)) || 0
      if (deposit) {
        changeAmountNew = parseFloat((changeAmountNew - deposit).toFixed(2)) || 0
      }
      this.$nextTick(() => {
        changeAmountNew = this.prefixNo === 'CGDD' || this.prefixNo === 'XSDD' ? 0 : changeAmountNew
        this.form.setFieldsValue({
          'discountMoney': discountMoneyNew, 'discountLastMoney': discountLastMoneyNew,
          'changeAmount': changeAmountNew, 'debt': 0
        })
        this.setZeroChangeAmount()
      });
    },
    //改变付款优惠
    onChangeDiscountMoney(e) {
      if (this.amountCalculationDisabled()) {
        return
      }
      const value = parseFloat(e.target.value) || 0
      let otherMoney = this.form.getFieldValue('otherMoney') ? parseFloat(this.form.getFieldValue('otherMoney')) || 0 : 0
      let deposit = this.form.getFieldValue('deposit') ? parseFloat(this.form.getFieldValue('deposit')) || 0 : 0
      let allTaxLastMoney = this.$refs.materialDataTable && this.$refs.materialDataTable.statisticsColumns &&
        this.$refs.materialDataTable.statisticsColumns.taxLastMoney ?
        parseFloat(this.$refs.materialDataTable.statisticsColumns.taxLastMoney) || 0 : 0
      let discountNew = allTaxLastMoney > 0 ? parseFloat((value / allTaxLastMoney * 100).toFixed(2)) || 0 : 0
      let discountLastMoneyNew = parseFloat((allTaxLastMoney - value).toFixed(2)) || 0
      let changeAmountNew = parseFloat((discountLastMoneyNew + otherMoney).toFixed(2)) || 0
      if (deposit) {
        changeAmountNew = parseFloat((changeAmountNew - deposit).toFixed(2)) || 0
      }
      this.$nextTick(() => {
        changeAmountNew = this.prefixNo === 'CGDD' || this.prefixNo === 'XSDD' ? 0 : changeAmountNew
        this.form.setFieldsValue({
          'discount': discountNew, 'discountLastMoney': discountLastMoneyNew,
          'changeAmount': changeAmountNew, 'debt': 0
        })
        this.setZeroChangeAmount()
      });
    },
    //其它费用
    onChangeOtherMoney(e) {
      if (this.amountCalculationDisabled()) {
        return
      }
      const value = parseFloat(e.target.value) || 0
      let discountLastMoney = parseFloat(this.form.getFieldValue('discountLastMoney')) || 0
      let deposit = this.form.getFieldValue('deposit') ? parseFloat(this.form.getFieldValue('deposit')) || 0 : 0
      let changeAmountNew = parseFloat((discountLastMoney + value).toFixed(2)) || 0
      if (deposit) {
        changeAmountNew = parseFloat((changeAmountNew - deposit).toFixed(2)) || 0
      }
      this.$nextTick(() => {
        this.form.setFieldsValue({ 'changeAmount': changeAmountNew, 'debt': 0 })
        this.setZeroChangeAmount()
      });
    },
    //改变扣除订金
    onChangeDeposit(e) {
      if (this.amountCalculationDisabled()) {
        return
      }
      const value = parseFloat(e.target.value) || 0
      let discountLastMoney = parseFloat(this.form.getFieldValue('discountLastMoney')) || 0
      let otherMoney = this.form.getFieldValue('otherMoney') ? parseFloat(this.form.getFieldValue('otherMoney')) || 0 : 0
      let changeAmountNew = parseFloat((discountLastMoney + otherMoney).toFixed(2)) || 0
      if (value) {
        changeAmountNew = parseFloat((changeAmountNew - value).toFixed(2)) || 0
      }
      this.$nextTick(() => {
        this.form.setFieldsValue({ 'changeAmount': changeAmountNew, 'debt': 0 })
        this.setZeroChangeAmount()
      });
    },
    //改变本次付款
    onChangeChangeAmount(e) {
      if (this.amountCalculationDisabled()) {
        return
      }
      const value = parseFloat(e.target.value) || 0
      let discountLastMoney = parseFloat(this.form.getFieldValue('discountLastMoney')) || 0
      let otherMoney = this.form.getFieldValue('otherMoney') ? parseFloat(this.form.getFieldValue('otherMoney')) || 0 : 0
      let deposit = this.form.getFieldValue('deposit') ? parseFloat(this.form.getFieldValue('deposit')) || 0 : 0
      let debtNew = parseFloat((discountLastMoney + otherMoney - value).toFixed(2)) || 0
      if (deposit) {
        debtNew = parseFloat((debtNew - deposit).toFixed(2)) || 0
      }
      this.$nextTick(() => {
        this.form.setFieldsValue({ 'debt': debtNew })
      });
    },
    //切换客户信息改变商品单价
    handleOrganChange(value) {
      let organId = value
      const amountDisabled = this.amountCalculationDisabled()
      this.getAllTable().then(tables => {
        return getListData(this.form, tables)
      }).then(allValues => {
        let detailArr = allValues.tablesValue[0].values
        let barCodeStr = ''
        for (let detail of detailArr) {
          if (detail.barCode) {
            barCodeStr += detail.barCode + ','
          }
        }
        if (barCodeStr) {
          let param = {
            barCode: barCodeStr,
            organId: organId,
            mpList: getMpListShort(Vue.ls.get('materialPropertyList')),  //扩展属性
            prefixNo: this.prefixNo
          }
          getMaterialByBarCode(param).then((res) => {
            if (res && res.code === 200) {
              let allLastMoney = 0
              let allTaxLastMoney = 0
              //获取单据明细列表信息
              let detailArr = allValues.tablesValue[0].values
              //构造新的列表数组，用于存放单据明细信息
              let newDetailArr = []
              for (let detail of detailArr) {
                if (detail.barCode) {
                  //如果唛头重复，就在给原来的数量加1
                  let mList = res.data
                  for (let i = 0; i < mList.length; i++) {
                    if (detail.barCode === mList[i].mBarCode) {
                      //由于改变了商品单价，需要同时更新相关金额和价税合计
                      let taxRate = detail.taxRate - 0 //税率
                      detail.unitPrice = mList[i].billPrice - 0 //单价
                      detail.allPrice = (detail.unitPrice * detail.operNumber).toFixed(2) - 0
                      detail.taxMoney = ((taxRate * 0.01) * detail.allPrice).toFixed(2) - 0
                      detail.taxLastMoney = (detail.allPrice + detail.taxMoney).toFixed(2) - 0
                    }
                  }
                  newDetailArr.push(detail)
                }
              }
              this.materialTable.dataSource = newDetailArr
              if (amountDisabled) {
                this.normalizeAmountsForDisabled()
                this.$nextTick(() => {
                  this.form.setFieldsValue({
                    'discount': 0,
                    'discountMoney': 0,
                    'discountLastMoney': 0,
                    'changeAmount': 0,
                    'debt': 0
                  })
                })
                return
              }
              //更新优惠后金额、本次付款等信息
              for (let newDetail of newDetailArr) {
                allLastMoney = allLastMoney + (parseFloat(newDetail.allPrice) || 0)
                allTaxLastMoney = allTaxLastMoney + (parseFloat(newDetail.taxLastMoney) || 0)
              }
              let discount = parseFloat(this.form.getFieldValue('discount')) || 0
              let otherMoney = this.form.getFieldValue('otherMoney') ? parseFloat(this.form.getFieldValue('otherMoney')) || 0 : 0
              let deposit = this.form.getFieldValue('deposit') ? parseFloat(this.form.getFieldValue('deposit')) || 0 : 0
              let discountMoney = parseFloat((discount * 0.01 * allTaxLastMoney).toFixed(2)) || 0
              let discountLastMoney = parseFloat((allTaxLastMoney - discountMoney).toFixed(2)) || 0
              let changeAmountNew = parseFloat((discountLastMoney + otherMoney).toFixed(2)) || 0
              if (deposit) {
                changeAmountNew = parseFloat((changeAmountNew - deposit).toFixed(2)) || 0
              }
              this.$nextTick(() => {
                changeAmountNew = this.prefixNo === 'XSDD' ? 0 : changeAmountNew
                this.form.setFieldsValue({
                  'discount': discount, 'discountMoney': discountMoney, 'discountLastMoney': discountLastMoney,
                  'changeAmount': changeAmountNew, 'debt': 0
                })
                this.setZeroChangeAmount()
              });
            }
          })
        }
      })
    },
    //切换收付款的金额为0
    setZeroChangeAmount() {
      if (this.prefixNo === 'CGRK' || this.prefixNo === 'CGTH' || this.prefixNo === 'XSCK' || this.prefixNo === 'XSTH') {
        if (this.zeroChangeAmountFlag) {
          let oldChangeAmount = this.form.getFieldValue('changeAmount') - 0
          this.form.setFieldsValue({ 'changeAmount': 0, 'debt': oldChangeAmount })
        }
      }
    },
    scanEnter() {
      this.scanStatus = false
      this.$nextTick(() => {
        this.$refs.scanBarCode.focus()
      })
    },
    //扫码之后回车
    scanPressEnter() {
      let that = this
      const amountDisabled = this.amountCalculationDisabled()
      if (this.scanBarCode) {
        this.getAllTable().then(tables => {
          return getListData(this.form, tables)
        }).then(allValues => {
          let param = {
            barCode: this.scanBarCode.trim(),
            organId: this.form.getFieldValue('organId'),
            mpList: getMpListShort(Vue.ls.get('materialPropertyList')),  //扩展属性
            prefixNo: this.prefixNo
          }
          getMaterialByBarCode(param).then((res) => {
            if (res && res.code === 200) {
              let hasFinished = false
              let allLastMoney = 0
              let allTaxLastMoney = 0
              //获取单据明细列表信息
              let detailArr = allValues.tablesValue[0].values
              //构造新的列表数组，用于存放单据明细信息
              let newDetailArr = []
              let hasAdd = false
              for (let detail of detailArr) {
                if (detail.barCode) {
                  //如果扫码结果和唛头重复，就在给原来的数量加1
                  if (detail.barCode === this.scanBarCode.trim() && !hasAdd) {
                    detail.operNumber = (detail.operNumber - 0) + 1
                    //由于改变了商品数量，需要同时更新相关金额和价税合计
                    let taxRate = detail.taxRate - 0 //税率
                    let unitPrice = detail.unitPrice - 0 //单价
                    detail.allPrice = (unitPrice * detail.operNumber).toFixed(2) - 0
                    detail.taxMoney = ((taxRate * 0.01) * detail.allPrice).toFixed(2) - 0
                    detail.taxLastMoney = (detail.allPrice + detail.taxMoney).toFixed(2) - 0
                    hasFinished = true
                    hasAdd = true
                  }
                  //如果扫码结果和序列号重复，就直接跳过
                  if (detail.snList === this.scanBarCode.trim()) {
                    this.$message.warning('抱歉，已经扫描过该序列号！');
                    hasFinished = true
                  }
                  newDetailArr.push(detail)
                }
              }
              if (!hasFinished) {
                //将扫码的唛头对应的商品加入列表
                let item = {}
                let mList = res.data
                if (mList && mList.length > 0) {
                  let mInfo = mList[0]
                  this.changeColumnShow(mInfo)
                  item.depotId = mInfo.depotId
                  item.barCode = mInfo.mBarCode
                  item.name = mInfo.name
                  item.standard = mInfo.standard
                  item.model = mInfo.model
                  item.color = mInfo.color
                  item.materialOther = mInfo.materialOther
                  item.stock = mInfo.stock
                  item.unit = mInfo.commodityUnit
                  item.sku = mInfo.sku
                  if (mInfo.mBarCode !== this.scanBarCode.trim()) {
                    if (this.prefixNo === 'LSCK' || this.prefixNo === 'CGTH' || this.prefixNo === 'XSCK' || this.prefixNo === 'QTCK') {
                      //此时给序列号赋值
                      item.snList = this.scanBarCode.trim()
                    }
                  }
                  item.operNumber = 1
                  item.unitPrice = mInfo.billPrice
                  item.allPrice = mInfo.billPrice
                  item.taxRate = 0
                  item.taxMoney = 0
                  item.taxLastMoney = mInfo.billPrice
                  newDetailArr.push(item)
                } else {
                  this.$message.warning('抱歉，此唛头不存在商品信息！');
                }
              }
              //组合和拆分单据给商品类型进行重新赋值
              for (let i = 0; i < newDetailArr.length; i++) {
                if (i === 0) {
                  newDetailArr[0].mType = '组合件'
                } else {
                  newDetailArr[i].mType = '普通子件'
                }
              }
              this.materialTable.dataSource = newDetailArr
              this.normalizeAmountsForDisabled()
              if (amountDisabled) {
                this.$nextTick(() => {
                  this.form.setFieldsValue({
                    'discount': 0,
                    'discountMoney': 0,
                    'discountLastMoney': 0,
                    'changeAmount': 0,
                    'debt': 0
                  })
                })
              } else {
                //更新优惠后金额、本次付款等信息
                for (let newDetail of newDetailArr) {
                  allLastMoney = allLastMoney + (parseFloat(newDetail.allPrice) || 0)
                  allTaxLastMoney = allTaxLastMoney + (parseFloat(newDetail.taxLastMoney) || 0)
                }
                let discount = parseFloat(this.form.getFieldValue('discount')) || 0
                let otherMoney = this.form.getFieldValue('otherMoney') ? parseFloat(this.form.getFieldValue('otherMoney')) || 0 : 0
                let deposit = this.form.getFieldValue('deposit') ? parseFloat(this.form.getFieldValue('deposit')) || 0 : 0
                let discountMoney = parseFloat((discount * 0.01 * allTaxLastMoney).toFixed(2)) || 0
                let discountLastMoney = parseFloat((allTaxLastMoney - discountMoney).toFixed(2)) || 0
                let changeAmountNew = parseFloat((discountLastMoney + otherMoney).toFixed(2)) || 0
                if (deposit) {
                  changeAmountNew = parseFloat((changeAmountNew - deposit).toFixed(2)) || 0
                }
                if (this.prefixNo === 'LSCK' || this.prefixNo === 'LSTH') {
                  this.$nextTick(() => {
                    this.form.setFieldsValue({ 'changeAmount': allLastMoney, 'getAmount': allLastMoney, 'backAmount': 0 })
                  });
                } else {
                  this.$nextTick(() => {
                    changeAmountNew = this.prefixNo === 'CGDD' || this.prefixNo === 'XSDD' ? 0 : changeAmountNew
                    this.form.setFieldsValue({
                      'discount': discount, 'discountMoney': discountMoney, 'discountLastMoney': discountLastMoney,
                      'changeAmount': changeAmountNew, 'debt': 0
                    })
                  });
                }
              }
              //置空扫码的内容
              this.scanBarCode = ''
              this.$refs.scanBarCode.focus()
              //自动下滑到最后一行
              setTimeout(function () {
                that.$refs.materialDataTable.resetScrollTop((newDetailArr.length + 1) * that.$refs.materialDataTable.rowHeight)
              }, 1000)
            }
          })
        })
      }
    },
    stopScan() {
      this.scanStatus = true
      this.scanBarCode = ''
    },
    onImport(prefixNo) {
      this.$refs.importItemModalForm.add(prefixNo);
    },
    importItemModalFormOk(data) {
      this.materialTable.dataSource = data
      this.normalizeAmountsForDisabled()
      const amountDisabled = this.amountCalculationDisabled()
      this.$nextTick(() => {
        for (let i = 0; i < data.length; i++) {
          this.changeColumnShow(data[i])
        }
        if (amountDisabled) {
          this.form.setFieldsValue({
            'discount': 0,
            'discountMoney': 0,
            'discountLastMoney': 0,
            'changeAmount': 0,
            'debt': 0
          })
          return
        }
        let discountLastMoney = 0
        for (let i = 0; i < data.length; i++) {
          discountLastMoney += parseFloat(data[i].taxLastMoney) || 0
        }
        this.form.setFieldsValue({ 'discountLastMoney': discountLastMoney })
      });
    },
    //保存并审核
    handleOkAndCheck() {
      this.billStatus = '1'
      this.handleOk()
    },
    //发起流程
    handleWorkflow() {
      if (this.model && this.model.number) {
        getPlatformConfigByKey({ "platformKey": "send_workflow_url" }).then((res) => {
          if (res && res.code === 200) {
            let sendWorkflowUrl = res.data.platformValue + '&no=' + this.model.number + '&type=1'
            this.$refs.modalWorkflow.show(this.model, sendWorkflowUrl, this.model.number, 1, 320)
            this.$refs.modalWorkflow.title = "发起流程"
          }
        })
      } else {
        this.$message.warning('请先保存单据后再提交流程！');
      }
    },
    //三联打印新版预览
    handlePrintPro(billType) {
      if (this.model.id) {
        getPlatformConfigByKey({ "platformKey": "bill_print_pro_url" }).then((res) => {
          if (res && res.code === 200) {
            let billPrintUrl = res.data.platformValue + '?no=' + this.model.number
            let billPrintHeight = document.documentElement.clientHeight - 260
            this.$refs.modalPrintPro.show(this.model, billPrintUrl, billPrintHeight)
            this.$refs.modalPrintPro.title = billType + "-三联打印新版预览"
          }
        })
      } else {
        this.$message.warning('请先保存单据后再打印！');
      }
    },
    //三联打印预览
    handlePrint(billType) {
      if (this.model.id) {
        getPlatformConfigByKey({ "platformKey": "bill_print_url" }).then((res) => {
          if (res && res.code === 200) {
            let billPrintUrl = res.data.platformValue + '?no=' + this.model.number
            let billPrintHeight = this.materialTable.dataSource.length * 50 + 600
            this.$refs.modalPrint.show(this.model, billPrintUrl, billPrintHeight)
            this.$refs.modalPrint.title = billType + "-三联打印预览"
          }
        })
      } else {
        this.$message.warning('请先保存单据后再打印！');
      }
    },
    //加载平台配置信息
    initPlatform() {
      getPlatformConfigByKey({ "platformKey": "bill_print_flag" }).then((res) => {
        if (res && res.code === 200) {
          this.billPrintFlag = res.data.platformValue === '1' ? true : false
        }
      })
    },
    //加载快捷按钮：供应商、客户、会员、结算账户、仓库
    initQuickBtn() {
      let btnStrList = Vue.ls.get('winBtnStrList') //按钮功能列表 JSON字符串
      if (btnStrList) {
        for (let i = 0; i < btnStrList.length; i++) {
          if (btnStrList[i].btnStr) {
            this.quickBtn.vendor = btnStrList[i].url === '/system/vendor' ? btnStrList[i].btnStr.indexOf(1) > -1 : this.quickBtn.vendor
            this.quickBtn.customer = btnStrList[i].url === '/system/customer' ? btnStrList[i].btnStr.indexOf(1) > -1 : this.quickBtn.customer
            this.quickBtn.member = btnStrList[i].url === '/system/member' ? btnStrList[i].btnStr.indexOf(1) > -1 : this.quickBtn.member
            this.quickBtn.account = btnStrList[i].url === '/system/account' ? btnStrList[i].btnStr.indexOf(1) > -1 : this.quickBtn.account
            this.quickBtn.depot = btnStrList[i].url === '/system/depot' ? btnStrList[i].btnStr.indexOf(1) > -1 : this.quickBtn.depot
          }
        }
      }
    },
    //动态替换扩展字段
    handleChangeOtherField() {
      let mpStr = getMpListShort(Vue.ls.get('materialPropertyList'))
      if (mpStr) {
        let mpArr = mpStr.split(',')
        if (mpArr.length === 3) {
          for (let i = 0; i < this.materialTable.columns.length; i++) {
            if (this.materialTable.columns[i].key === 'otherField1') {
              this.materialTable.columns[i].title = mpArr[0]
            }
            if (this.materialTable.columns[i].key === 'otherField2') {
              this.materialTable.columns[i].title = mpArr[1]
            }
            if (this.materialTable.columns[i].key === 'otherField3') {
              this.materialTable.columns[i].title = mpArr[2]
            }
          }
        }
      }
    }
  }
}