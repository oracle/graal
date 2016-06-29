# Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# 
# R Script to Plot Graal Compiler related Data
#
# This script contains some simple plotting functions
# for graphical evaluation of the DebugMethodMetrics as
# described in docs/Debugging.md.
#
########################################################

#
#####  Globals  #####
#
# A static csv file containing csv dump of graal method metrics
DATA_PATH <- ""
#
# Raw data read from the dump file, must not be modified after read
RAW_DATA <- c()
#
# clear env
DATA_WIDE <- c()
#
# names of dacapo benchmarks
dacapos <- c("avrora","batik","fop","h2","jython","luindex","lusearch","pmd","sunflow","tomcat","tradebeans","tradesoap","xalan")
#### End Globals ####


#### General Functions ####
ensurePackage <- function(name){
  found <- require(name,character.only = TRUE)
  if(!found){
    stop(paste("Cannot attach package ",name))
  }
}


prepareEnv  <- function(){
  ## used for plotting information
  ensurePackage("ggplot2")
  ## used for re-shaping raw (long) data format to wide data format
  ensurePackage("tidyr")
  ensurePackage("plyr")
  ensurePackage("dplyr")
  ensurePackage("reshape2")
  ensurePackage("grid")
  ensurePackage("gridExtra")
  # interative plots are not default
  # ensurePackage("plotly")
}



readDataRaw <- function(path){
  print(paste("Reading raw data from file ",path,"..."))
  data <- read.csv(path,sep = ";",fill = TRUE,header = FALSE)
  # register an id column for dcast
  data$id <- seq.int(nrow(data))
  return(data)
}

readData <- function(){
  RAW_DATA <<- readDataRaw(DATA_PATH)
}

plotRegisterAllocation <- function(){
  ggplot(WIDE_DATA, aes(x = GeneratedLIRInstructions, y = LIRPhaseTime_AllocationStage_Accm/1000000,label=methodName)) + 
    geom_point(alpha=0.2)+
    geom_smooth(method = "lm")+
    geom_smooth()
   # lims(x = c(0,1000),y=c(0,30)) + 
   # ggplotly()
}

prepareData <- function(combineNameWithIdentity=TRUE){
  # name raw data to make access easier
  names(RAW_DATA) <- c("methodName","methodIdentity","compilationIndex","globalGraphId","metricName","metricValue")
  # drop the globalGraphIds (they are not necessary for now)
  RAW_DATA <<- RAW_DATA %>% select(methodName,methodIdentity,compilationIndex,metricName,metricValue)
  # method metric data comes in long data format, we want it wide
  WIDE_DATA <<- reshape2::dcast(RAW_DATA, 
                                methodName + methodIdentity + compilationIndex ~ metricName,fill = 0,value.var = "metricValue")
  if(combineNameWithIdentity){
    WIDE_DATA <<- WIDE_DATA %>%
    mutate(methodName=paste(methodName,as.character(methodIdentity)))
  }
}
#### End General Functions ####

tryCatch(expr = {
  # main
  prepareEnv()
  readData()
  prepareData()
},
error = function(e) {
  print(e)
}
)

plotDacaposRegisterAllocationTime <-function(basepath){
  grid.newpage()
  pushViewport(viewport(layout = grid.layout(13, 1)))
  vplayout <- function(x, y){
    print(paste("Using args for viewport ",as.character(x),as.character(y)))
    return(viewport(layout.pos.row = x, layout.pos.col = y))
  } 
  i <- 0
  for (benchname in dacapos) {
    i <- i + 1
    tryCatch(expr = {
      # reuse global data path
      DATA_PATH <<- paste(basepath,benchname,sep = "")
      print(paste("Prossing file ",DATA_PATH,"..."))
      # read the data
      readData()
      prepareData()
      py <- ggplot(WIDE_DATA, aes(x = GeneratedLIRInstructions, y = LIRPhaseTime_AllocationStage_Accm/1000000,label=methodName)) + 
        geom_point(alpha=0.2)+
        geom_smooth(method = "lm")+
        lims(x = c(0,5000),y=c(0,100)) + 
        geom_smooth()
      print(py, vp = vplayout(i,1)) 
      #ggplotly()
    },
    error = function(e) {
      print(e)
    }
    )
  }
}

phasesSum <- function(phaseName){
  phases <- WIDE_DATA %>% 
    filter(compilationIndex==0) %>% 
    select(methodName, starts_with(phaseName))
  sum <- sum(phases[,2])
  print(paste("Phases Sum Flat ",sum/ 1000000))
}

# if time -> phase times else phase mem
plotPhases <- function(highestNPhase=10,highestNMethods=10,flat=TRUE,time=TRUE,compilationINDEX=0,interactive=FALSE){
  # e.g. plot phase times (of those methods with compilation id=0 and where the phases ran longests (sum))
  # data gathered via mx --vm jvmci dacapo -G:Count= -G:Time= -G:TrackMemUse= -G:MethodMeter= 
  # -G:GlobalMetricsInterceptedByMethodMetrics=Timers,Counters,MemUseTrackers fop -n 10
  # we look at one compilation only per method
  phases <- WIDE_DATA %>% 
    filter(compilationIndex==compilationINDEX) %>% 
    select(methodName, starts_with(ifelse(time,"PhaseTime","PhaseMemUse"))) %>% 
    select(methodName,ends_with(ifelse(flat, "Flat","Accm"),ignore.case = TRUE)) %>% # in ms
    select(everything(),-contains("Instance")) %>%
    select(everything(),-contains("HighTier")) %>%
    select(everything(),-contains("MidTier")) %>%
    select(everything(),-contains("LowTier")) %>%
    mutate_each(funs(div = . / 1000000),starts_with(ifelse(time,"PhaseTime","PhaseMemUse")))

  # we use the 10 highest phase runs
  colSumsOf <-colSums(phases %>% select(starts_with(ifelse(time,"PhaseTime","PhaseMemUse"))))
  highestIndices <- order(colSumsOf,decreasing = TRUE)[1:highestNPhase]
  # 1. index is method name
  highestIndices <- highestIndices + 1
  phases <-phases[c(1,highestIndices)]
  
  # get the x highest methods per phase runs
  rMax <- rowSums(phases[,2:ncol(phases)])
  maxIndices <- order(rMax,decreasing = TRUE)[1:highestNMethods]
  phases <- phases[maxIndices,]
  
  melted <- melt(phases,id.vars = "methodName")
  names(melted) <- c("methodName","phase","time")
  ggplot(melted,aes(x=methodName,y=time,fill=factor(phase))) +
    geom_bar(stat = "identity")+
    theme_bw()+
    theme(axis.text.x = element_text(angle = 90, vjust = 1, hjust=1))+
    scale_size_area() + 
    xlab("Method") +
    ylab(ifelse(time,"Time(ms)","Memory(Mb)")) +
    ggtitle(paste(highestNMethods," methods with the highest (sum per phase) ",highestNPhase," phase ",
                  ifelse(time,"PhaseTime","PhaseMemUse")))
  if(interactive){
    ggplotly()
  }else{
    print(ggplot2::last_plot()) 
  }
}

plotLongestCompileTime <- function(nrOfMethods=10,interactive=FALSE){
  # e.g. plot longest compile time (of those methods with compilation id=0 and where the phases ran longests (sum))
  # data gathered via mx --vm jvmci dacapo -G:Count= -G:Time= -G:TrackMemUse= -G:MethodMeter= 
  # -G:GlobalMetricsInterceptedByMethodMetrics=Timers,Counters,MemUseTrackers fop -n 10
  # we look at one compilation only per method  
  timeSize <- WIDE_DATA %>% 
    filter(compilationIndex==0) %>% 
    select(methodName, matches("CompilationTime_Accm")) %>%
    mutate(time=CompilationTime_Accm / 1000000)
  timeSize <- data.frame(timeSize$methodName,timeSize$time)
  names(timeSize) <- c("methodName","time")
  
  # we use the 10 highest methods
  # get sum of highest phases
  maxIndices <- order(timeSize[,2],decreasing = TRUE)[1:nrOfMethods]
  timeSize <- timeSize[maxIndices,]
  ggplot(timeSize,
         aes(
           x=timeSize$methodName,
           y=timeSize$time
         )    
  )+ 
    geom_bar(stat = "identity",fill="gray")+
    geom_text(aes(label = time),size=5)+ 
    theme_bw()+
    theme(axis.text.x = element_text(angle = 90, vjust = 1, hjust=1)) +
    scale_size_area() + 
    xlab("Method") +
    ylab("Times(ms)")
   if(interactive){
    ggplotly()
  }else{
    print(ggplot2::last_plot()) 
  }
}

plotLongestCodeInstallTime <- function(nrOfMethods=10,interactive=FALSE){
  timeSize <- WIDE_DATA %>% 
    filter(compilationIndex==0) %>% 
    select(methodName, matches("CodeInstallation_Accm")) %>%
    mutate(time=CodeInstallation_Accm / 1000000)
  timeSize <- data.frame(timeSize$methodName,timeSize$time)
  names(timeSize) <- c("methodName","time")
  
  # we use the 10 highest methods
  # get sum of highest phases
  maxIndices <- order(timeSize[,2],decreasing = TRUE)[1:nrOfMethods]
  timeSize <- timeSize[maxIndices,]
  ggplot(timeSize,
         aes(
           x=timeSize$methodName,
           y=timeSize$time
         )    
  )+
    geom_bar(stat = "identity",fill="gray")+
    geom_text(aes(label = time),size=5)+ 
    theme_bw()+
    theme(axis.text.x = element_text(angle = 90, vjust = 1, hjust=1)) +
    scale_size_area() + 
    xlab("Method") +
    ylab("Times(ms)")
  if(interactive){
    ggplotly()
  }else{
    print(ggplot2::last_plot()) 
  }
}

plotLongestTime <- function(nrOfMethods=10,interactive=FALSE){
  timeSize <- WIDE_DATA %>% 
    filter(compilationIndex==0) %>% 
    select(methodName, matches("CompilationTime_Accm|CodeInstallation_Accm")) %>%
    mutate(time=(CompilationTime_Accm+CodeInstallation_Accm) / 1000000)
  timeSize <- data.frame(timeSize$methodName,timeSize$time)
  names(timeSize) <- c("methodName","time")
  maxIndices <- order(timeSize[,2],decreasing = TRUE)[1:nrOfMethods]
  timeSize <- timeSize[maxIndices,]
  ggplot(timeSize,
         aes(
           x=timeSize$methodName,
           y=timeSize$time
         )    
  )+
    geom_bar(stat = "identity",fill="gray")+
    geom_text(aes(label = time),size=5)+ 
    theme_bw()+
    theme(axis.text.x = element_text(angle = 90, vjust = 1, hjust=1)) +
    scale_size_area() + 
    xlab("Method") +
    ylab("Times(ms)")
  if(interactive){
    ggplotly()
  }else{
   print(ggplot2::last_plot()) 
  }
}


# plot the most n methods that where compiled the most
plotNrOfCompilations <- function(nrOfMethods=10,interactive=FALSE){
  # plot all methods that where compiled including nr of compilations
  collapseCompilations <- WIDE_DATA %>% select(methodName, compilationIndex)  %>% ddply(.(methodName),nrow)
  names(collapseCompilations) <- c("methodName","compilations")
  highestIndices <- order(collapseCompilations$compilations,decreasing = TRUE)[1:nrOfMethods]
  collapseCompilations <-collapseCompilations[highestIndices,]
  ggplot(collapseCompilations,
         aes(
           x=collapseCompilations$methodName,
           y=collapseCompilations$compilations
         )    
  )+
    geom_bar(stat = "identity",fill="gray")+
    geom_text(aes(label = compilations+1),size=5)+ 
    theme_bw()+
    theme(axis.text.x = element_text(angle = 90, vjust = 1, hjust=1))+
    scale_size_area() + 
    xlab("Method") +
    ylab("Nr of Compilations") +
    ggtitle(paste("Nr of compilations for the ",nrOfMethods, " most compiled methods"))
  if(interactive){
    ggplotly() 
  }else{
    print(ggplot2::last_plot()) 
  }
}








