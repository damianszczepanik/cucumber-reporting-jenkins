var barChart = (function () {
    "use strict";
    var myBarChart = {};

    function createBarChart(chartElementName, chartContainer, inputData, numberOfLayers, numberOfGroups) {
        var n = numberOfLayers,
            m = numberOfGroups,
            data = d3.layout.stack()(inputData),
            color = d3.interpolateRgb("#0AC92B", "#CD0000"),
            margin = 10,
            leftMargin = 50,
            rightMargin = 50,
            bottomMargin = 120,
            topMargin = 10,
            width = chartContainer.width(),
            height = chartContainer.height() - 0.5 - margin;
        var maxLabelLength = 20;

        //this stops the columns from becoming to big
        if ((width / m) > 50) {
            width = m * 50;
        }

        var mx = m,
            my = d3.max(data, function (d) {
                return d3.max(d, function (d) {
                    return d.y0 + d.y;
                });
            }),
            mz = d3.max(data, function (d) {
                return d3.max(d, function (d) {
                    return d.y;
                });
            }),
            y0 = function (d) {
                return height - d.y0 * height / my;
            },
            y1 = function (d) {
                return height - (d.y + d.y0) * height / my;
            },
            x = function (d) {
                return (d.x * width / mx);
            },
            x0 = leftMargin,
            y2 = function (d) {
                return d.y * height / mz;
            }; // or `my` to not rescale

        var vis = d3.select("#" + chartElementName)
            .append("svg")
            .attr("width", width + leftMargin + rightMargin)
            .attr("height", height + margin + bottomMargin); //todo: handle max char length for tags

        var i = 0,
            maxYRangeStacked = 0,
            maxYRangeGrouped = 0;

        for (i = 0; i < inputData[0].length; i += 1) {
            if (maxYRangeStacked < (inputData[0][i].y + inputData[1][i].y)) {
                maxYRangeStacked = (inputData[0][i].y + inputData[1][i].y);
            }
            if (maxYRangeGrouped < (inputData[0][i].y)) {
                maxYRangeGrouped = inputData[0][i].y;
            }
            if (maxYRangeGrouped < (inputData[1][i].y)) {
                maxYRangeGrouped = inputData[1][i].y;
            }
        }

        var axisGroup = vis.append("svg:g").
            attr("transform", "translate(" + (margin + 2.5) + ",0)");

        var yScaleStacked = d3.scale.linear().domain([0, maxYRangeStacked]).range([0, height]);
        var yScaleGrouped = d3.scale.linear().domain([0, maxYRangeGrouped]).range([0, height]);

        var yticks = vis.selectAll('.yTicks')
            .data(yScaleStacked.ticks(10))
            .enter().append('svg:g')
            .attr('transform', function (d) {
                return "translate(" + (leftMargin) + "," + (height - yScaleStacked(d) + topMargin) +  ")";
            })
            .attr('class', 'yTick');

        yticks.append("svg:line").
            attr("x1", 0).
            attr("y1", function (d) {
                return 0;
            }).
            attr("x2", width).
            attr("y2", function (d) {
                return 0;
            }).
            attr("stroke", "lightgrey");

        yticks.append('svg:text').
            text(function (d) {
                return d;
            }).
            attr('text-anchor', 'start').
            attr("class", "yTickText").
            attr("x", width + 8).
            attr("y", function (d) {
                return 0;
            }).
            attr('dy', 2).
            attr('dx', -4);

        var layers = vis.selectAll("g.layer")
            .data(data)
            .enter().append("g")
            .style("fill", function (d, i) {
                return color(i / (n - 1));
            })
            .attr("class", "layer")
            .attr('transform', function (d) { return "translate(0," + topMargin + ")"; });

        var bars = layers.selectAll("g.bar")
            .data(function (d) {
                return d;
            })
            .enter().append("g")
            .attr("class", "bar")
            .attr("transform", function (d) {
                return "translate(" + (x(d)) + ",0)";
            });

        bars.append("rect")
            .attr("width", x({x:0.9}))
            .attr("x", leftMargin)
            .attr("y", height)
            .attr("height", 0)
            .transition()
            .delay(function (d, i) {
                return i * 10;
            })
            .attr("y", y1)
            .attr("height", function (d) {
                return y0(d) - y1(d);
            });

        var gText = vis.selectAll("text.label")
            .data(data[0])
            .enter().append("svg:g")
            .attr("transform", function (d, i) {
                return "translate(" + (leftMargin + x(d)) + "," + (height + margin + 10 + topMargin) + ")";
            });

        gText.append("text")
            .text(function (d, i) {
                if (d.label.length > maxLabelLength) {
                    return (d.label.substring(0, maxLabelLength - 3) + "...");
                }
                return d.label;
            })
            .attr("text-anchor", "end")
            .attr("class", "label")
            .attr("transform", function (d, i) {
                return "translate(20,0) rotate(-45,0,0)";
            });


        axisGroup.selectAll(".xTicks").
            data(data[0]).
            enter().append("svg:line").
            attr("x1", function (d) {
                return x(d) + leftMargin + margin;
            }).
            attr("y1", height + topMargin).
            attr("x2", function (d) {
                return x(d) + leftMargin + margin;
            }).
            attr("y2", height + topMargin + 5).
            attr("stroke", "black").
            attr("class", "xTicks");

        var xAxis = vis.append("svg:line").
            attr("x1", leftMargin - (margin / 2)).
            attr("y1", height + topMargin).
            attr("x2", leftMargin + width).
            attr("y2", height + topMargin).
            attr("stroke", "black").
            attr("class", "xTicks");

        var yAxis = vis.append("svg:line").
            attr("x1", leftMargin + width).
            attr("y1", topMargin).
            attr("x2", leftMargin + width).
            attr("y2", height + topMargin).
            attr("stroke", "black").
            attr("class", "yTicks");

        function transitionGroup() {
            var group = d3.selectAll("#" + chartElementName);

            group.select("#group")
                .attr("class", "first active");

            group.select("#stack")
                .attr("class", "last");

            function transitionEnd() {
                d3.select(this)
                    .transition()
                    .duration(500)
                    .attr("y", function (d) {
                        return height - y2(d);
                    })
                    .attr("height", y2);
            }

            group.selectAll("g.layer rect")
                .transition()
                .duration(500)
                .delay(function (d, i) {
                    return (i % m) * 20;
                })
                .attr("x", function (d, i) {
                    return x({x:(0.9 * ~~(i / m) / n)}) + leftMargin;
                })
                .attr("width", x({x:0.9 / n}))
                .each("end", transitionEnd);

            group.selectAll("text.yTickText")
                .text(function (d, i) {
                    return Math.round(yScaleGrouped.invert(yScaleStacked(d)));
                });
        }

        function transitionStack() {
            var stack = d3.select("#" + chartElementName);

            stack.select("#group")
                .attr("class", "first");

            stack.select("#stack")
                .attr("class", "last active");

            function transitionEnd() {
                d3.select(this)
                    .transition()
                    .duration(500)
                    .attr("x", leftMargin)
                    .attr("width", x({x : 0.9}));
            }

            stack.selectAll("g.layer rect")
                .transition()
                .duration(500)
                .delay(function (d, i) {
                    return (i % m) * 20;
                })
                .attr("y", y1)
                .attr("height", function (d) {
                    return y0(d) - y1(d);
                })
                .each("end", transitionEnd);

            stack.selectAll("text.yTickText")
                .text(function (d, i) {
                    return d;
                });
        }

        myBarChart.transitionGroup = transitionGroup;
        myBarChart.transitionStack = transitionStack;
    }

    function createLegend(element) {

    }

    myBarChart.createBarChart = createBarChart;
    myBarChart.createLegend = createLegend;

    return myBarChart;
}());